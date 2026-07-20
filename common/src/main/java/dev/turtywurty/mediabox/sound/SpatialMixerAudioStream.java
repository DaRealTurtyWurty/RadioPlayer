package dev.turtywurty.mediabox.sound;

import dev.turtywurty.mediabox.sound.process.SpeakerProcessingContext;
import dev.turtywurty.mediabox.sound.process.SpeakerProcessor;
import dev.turtywurty.mediabox.sound.process.SpeakerProcessorFactory;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpatialMixerAudioStream implements AudioStream {
    // Minecraft normally requests one-second buffers. Keep these shorter for
    // responsive spatial mixing, but long enough that the OpenAL queue cannot
    // underrun between sound-engine updates.
    private static final int MAX_OUTPUT_BYTES = 32_768;
    private static final int OUTPUT_BUFFER_COUNT = 4;
    private static final float MIN_NORMALIZATION_GAIN = 1.0F;
    private static final float SPEAKER_ARRAY_LOUDNESS_EXPONENT = 0.35F;

    private final Map<EmitterKey, List<SpeakerDriverRuntime>> driversByEmitter = new HashMap<>();

    private final ClientAudioSource source;
    private final AudioStream decodedStream;
    private final AudioFormat inputFormat;
    private final AudioFormat outputFormat;
    private final ByteBuffer[] outputBuffers = new ByteBuffer[OUTPUT_BUFFER_COUNT];
    private int nextOutputBuffer;
    private boolean synchronizedBeforeBuffering;
    private final Deque<Integer> queuedFrameCounts = new ArrayDeque<>();
    private long processedFrames;

    public SpatialMixerAudioStream(ClientAudioSource source, AudioStream decodedStream) {
        this.source = source;
        this.decodedStream = decodedStream;
        this.inputFormat = decodedStream.getFormat();
        this.outputFormat = isMixableFormat(this.inputFormat) ?
                new AudioFormat(this.inputFormat.getSampleRate(), 16, 2, true, false) :
                this.inputFormat;
    }

    public void onPlaybackStarted() {
        if (this.decodedStream instanceof PlaybackStartedAudioStream playbackStartedStream) {
            playbackStartedStream.onPlaybackStarted();
        }
    }

    public void onBuffersProcessed(int count) {
        for (int index = 0; index < count && !this.queuedFrameCounts.isEmpty(); index++) {
            this.processedFrames += this.queuedFrameCounts.removeFirst();
        }
    }

    public void onPlaybackCursor(int currentBufferSampleOffset) {
        publishPlaybackCursor(this.processedFrames + Math.max(0, currentBufferSampleOffset));
    }

    public void onPlaybackCursor(
            double currentBufferOffsetSeconds,
            double outputLatencySeconds,
            float playbackPitch
    ) {
        double sampleRate = this.outputFormat.getSampleRate();
        double mixedFrames = this.processedFrames
                + Math.max(0.0, currentBufferOffsetSeconds) * sampleRate;
        double queuedForOutputFrames = Math.max(0.0, outputLatencySeconds)
                * sampleRate
                * Math.max(0.01F, playbackPitch);
        publishPlaybackCursor((long) Math.floor(Math.max(0.0, mixedFrames - queuedForOutputFrames)));
    }

    private void publishPlaybackCursor(long playedFrames) {
        if (this.decodedStream instanceof PlaybackStartedAudioStream playbackStartedStream) {
            playbackStartedStream.onPlaybackCursor(playedFrames);
        }
    }

    private static boolean isMixableFormat(AudioFormat format) {
        return format.getSampleSizeInBits() == 16 &&
                (format.getChannels() == 1 || format.getChannels() == 2) &&
                !format.isBigEndian();
    }

    private static SpatialGain calculateGain(AudioEmitter emitter, ClientAudioSource source) {
        Vec3 listenerPos = source.getListenerPos();
        Vec3 listenerRight = source.getListenerRight();

        Vec3 emitterCenter = Vec3.atCenterOf(emitter.pos());
        Vec3 listenerToEmitter = emitterCenter.subtract(listenerPos);

        double distance = listenerToEmitter.length();
        if (distance <= 0.001D)
            return new SpatialGain(emitter.gain(), emitter.gain());

        Vec3 direction = listenerToEmitter.normalize();

        SpeakerProfile profile = emitter.type().profile();
        if (distance > profile.maxRange())
            return new SpatialGain(0.0f, 0.0f);

        float attenuation = (float) (1.0D / (1.0D + distance * distance * profile.distanceFalloff()));
        float pan = (float) direction.dot(listenerRight);
        pan = Math.clamp(pan, -1.0f, 1.0f);

        float leftPan = Mth.sqrt((1.0f - pan) / 2.0f);
        float rightPan = Mth.sqrt((1.0f + pan) / 2.0f);

        float cone = calculateConeGain(emitter, listenerPos, profile);

        float gain = emitter.gain() * profile.gain() * attenuation * cone;
        return new SpatialGain(gain * leftPan, gain * rightPan);
    }

    private static float calculateConeGain(AudioEmitter emitter, Vec3 listenerPos, SpeakerProfile profile) {
        Vec3 emitterCenter = Vec3.atCenterOf(emitter.pos());
        Vec3 speakerForward = Vec3.atLowerCornerOf(emitter.getFacingNormal()).normalize();
        Vec3 speakerToListener = listenerPos.subtract(emitterCenter).normalize();

        float dot = (float) speakerForward.dot(speakerToListener);
        return Math.clamp((dot + profile.coneWidth()) / (1.0F + profile.coneWidth()), profile.coneMinGain(), 1.0F);
    }

    private static short floatToShort(float sample) {
        return (short) (Mth.clamp(sample, -1.0F, 1.0F) * Short.MAX_VALUE);
    }

    private static float softClip(float sample) {
        return sample / (1.0F + Math.abs(sample));
    }

    @Override
    public @NonNull AudioFormat getFormat() {
        return this.outputFormat;
    }

    @Override
    public @NonNull ByteBuffer read(int size) throws IOException {
        if (!this.synchronizedBeforeBuffering) {
            this.synchronizedBeforeBuffering = true;
            if (this.decodedStream instanceof FfmpegAudioStream ffmpegStream) {
                ffmpegStream.synchronizeToVideoNow();
            }
        }

        int outputSize = requestedOutputSize(size);
        int inputSize = this.inputFormat.getChannels() == 1 && isMixableFormat(this.inputFormat) ? outputSize / 2 : outputSize;
        ByteBuffer input = this.decodedStream.read(inputSize);
        ByteBuffer output = mix(input);
        this.queuedFrameCounts.addLast(output.remaining() / this.outputFormat.getFrameSize());
        return output;
    }

    @Override
    public void close() throws IOException {
        this.decodedStream.close();
    }

    private ByteBuffer mix(ByteBuffer input) {
        List<AudioEmitter> emitters = this.source.getEmitters();
        if (!isMixableFormat(this.inputFormat))
            return input;

        List<EmitterMixRuntime> runtimes = createMixRuntimes(emitters);
        float totalGain = 0.0F;
        for (EmitterMixRuntime runtime : runtimes) {
            totalGain += runtime.maximumGain();
        }

        float normalizedGain = Math.max(MIN_NORMALIZATION_GAIN, totalGain);
        float normalization = (float) Math.pow(normalizedGain, SPEAKER_ARRAY_LOUDNESS_EXPONENT - 1.0F);

        int outputSize = this.inputFormat.getChannels() == 1 ? input.remaining() * 2 : input.remaining();
        ByteBuffer output = acquireOutputBuffer(outputSize);
        input.order(ByteOrder.LITTLE_ENDIAN);

        while (input.remaining() >= this.inputFormat.getFrameSize()) {
            float mono = readMonoSample(input);

            float outLeft = 0.0f;
            float outRight = 0.0f;

            for (EmitterMixRuntime runtime : runtimes) {
                float processed = runtime.process(mono);
                outLeft += processed * runtime.gain().left();
                outRight += processed * runtime.gain().right();
            }

            outLeft *= normalization;
            outRight *= normalization;

            output.putShort(floatToShort(softClip(outLeft)));
            output.putShort(floatToShort(softClip(outRight)));
        }

        while (input.hasRemaining()) {
            output.put(input.get());
        }

        output.flip();
        return output;
    }

    private float readMonoSample(ByteBuffer input) {
        if (this.inputFormat.getChannels() == 1)
            return input.getShort() / 32768.0f;

        short inLeft = input.getShort();
        short inRight = input.getShort();
        return ((inLeft / 32768.0f) + (inRight / 32768.0f)) / 2.0f;
    }

    private List<EmitterMixRuntime> createMixRuntimes(List<AudioEmitter> emitters) {
        List<EmitterMixRuntime> runtimes = new ArrayList<>(emitters.size());
        for (AudioEmitter emitter : emitters) {
            SpatialGain gain = calculateGain(emitter, this.source);
            runtimes.add(new EmitterMixRuntime(
                    gain,
                    new SpeakerProcessingContext(this.outputFormat.getSampleRate(), emitter),
                    getDriversFor(emitter)
            ));
        }

        return runtimes;
    }

    private List<SpeakerDriverRuntime> getDriversFor(AudioEmitter emitter) {
        return this.driversByEmitter.computeIfAbsent(EmitterKey.from(emitter), _ ->
                emitter.type().profile().drivers().stream()
                        .map(SpeakerDriverRuntime::create)
                        .toList()
        );
    }

    private int requestedOutputSize(int requestedSize) {
        if (!isMixableFormat(this.inputFormat))
            return requestedSize;

        int frameSize = this.outputFormat.getFrameSize();
        int cappedSize = Math.min(requestedSize, MAX_OUTPUT_BYTES);
        return Math.max(frameSize, cappedSize - cappedSize % frameSize);
    }

    private ByteBuffer acquireOutputBuffer(int size) {
        int index = this.nextOutputBuffer++ % OUTPUT_BUFFER_COUNT;
        ByteBuffer buffer = this.outputBuffers[index];
        if (buffer == null || buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
            this.outputBuffers[index] = buffer;
        }

        buffer.clear();
        buffer.limit(size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer;
    }

    private record SpatialGain(float left, float right) {
    }

    private record EmitterKey(BlockPos pos, SpeakerType type) {
        private static EmitterKey from(AudioEmitter emitter) {
            return new EmitterKey(emitter.pos(), emitter.type());
        }
    }

    private record EmitterMixRuntime(
            SpatialGain gain,
            SpeakerProcessingContext context,
            List<SpeakerDriverRuntime> drivers
    ) {
        private float maximumGain() {
            return Math.max(this.gain.left(), this.gain.right());
        }

        private float process(float sample) {
            float processed = 0.0F;
            for (SpeakerDriverRuntime driver : this.drivers) {
                processed += driver.process(sample, this.context);
            }

            return processed;
        }
    }

    private record SpeakerDriverRuntime(float gain, List<SpeakerProcessor> processors) {
        private static SpeakerDriverRuntime create(SpeakerDriverProfile profile) {
            return new SpeakerDriverRuntime(
                    profile.gain(),
                    profile.processors().stream()
                            .map(SpeakerProcessorFactory::create)
                            .toList());
        }

        private float process(float sample, SpeakerProcessingContext context) {
            float processed = sample * this.gain;
            for (SpeakerProcessor processor : this.processors) {
                processed = processor.process(processed, context);
            }

            return processed;
        }
    }
}
