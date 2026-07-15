package dev.turtywurty.mediaplayer.sound;

import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessingContext;
import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessor;
import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessorFactory;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RadioMixerAudioStream implements AudioStream {
    private static final int MAX_OUTPUT_BYTES = 4_096;
    private static final int OUTPUT_BUFFER_COUNT = 4;
    private static final float MIN_NORMALIZATION_GAIN = 1.0F;
    private static final float SPEAKER_ARRAY_LOUDNESS_EXPONENT = 0.35F;

    private final Map<EmitterKey, List<SpeakerDriverRuntime>> driversByEmitter = new HashMap<>();

    private final RadioAudioSource source;
    private final AudioStream decodedStream;
    private final AudioFormat inputFormat;
    private final AudioFormat outputFormat;
    private final ByteBuffer[] outputBuffers = new ByteBuffer[OUTPUT_BUFFER_COUNT];
    private int nextOutputBuffer;

    public RadioMixerAudioStream(RadioAudioSource source, AudioStream decodedStream) {
        this.source = source;
        this.decodedStream = decodedStream;
        this.inputFormat = decodedStream.getFormat();
        this.outputFormat = isMixableFormat(this.inputFormat) ?
                new AudioFormat(this.inputFormat.getSampleRate(), 16, 2, true, false) :
                this.inputFormat;
    }

    private static boolean isMixableFormat(AudioFormat format) {
        return format.getSampleSizeInBits() == 16 &&
                (format.getChannels() == 1 || format.getChannels() == 2) &&
                !format.isBigEndian();
    }

    private static SpatialGain calculateGain(RadioAudioEmitter emitter, RadioAudioSource source) {
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

    private static float calculateConeGain(RadioAudioEmitter emitter, Vec3 listenerPos, SpeakerProfile profile) {
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
        int outputSize = requestedOutputSize(size);
        int inputSize = this.inputFormat.getChannels() == 1 && isMixableFormat(this.inputFormat) ? outputSize / 2 : outputSize;
        ByteBuffer input = this.decodedStream.read(inputSize);
        return mix(input);
    }

    @Override
    public void close() throws IOException {
        this.decodedStream.close();
    }

    private ByteBuffer mix(ByteBuffer input) {
        List<RadioAudioEmitter> emitters = this.source.getEmitters();
        if (emitters.isEmpty() || !isMixableFormat(this.inputFormat))
            return input;

        int outputSize = this.inputFormat.getChannels() == 1 ? input.remaining() * 2 : input.remaining();
        ByteBuffer output = acquireOutputBuffer(outputSize);
        input.order(ByteOrder.LITTLE_ENDIAN);

        while (input.remaining() >= this.inputFormat.getFrameSize()) {
            float mono = readMonoSample(input);

            float outLeft = 0.0f;
            float outRight = 0.0f;
            float totalGain = 0.0F;

            for (RadioAudioEmitter emitter : emitters) {
                MixContribution contribution = calculateContribution(mono, emitter);
                outLeft += contribution.left();
                outRight += contribution.right();
                totalGain += contribution.gain();
            }

            float normalizedGain = Math.max(MIN_NORMALIZATION_GAIN, totalGain);
            float normalization = (float) Math.pow(normalizedGain, SPEAKER_ARRAY_LOUDNESS_EXPONENT - 1.0F);
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

    private MixContribution calculateContribution(float sample, RadioAudioEmitter emitter) {
        float emitterSample = processForEmitter(sample, emitter);
        SpatialGain gain = calculateGain(emitter, this.source);
        return new MixContribution(
                emitterSample * gain.left(),
                emitterSample * gain.right(),
                Math.max(gain.left(), gain.right()));
    }

    private float processForEmitter(float sample, RadioAudioEmitter emitter) {
        var context = new SpeakerProcessingContext(
                this.outputFormat.getSampleRate(),
                emitter
        );

        float processed = 0.0F;
        for (SpeakerDriverRuntime driver : getDriversFor(emitter)) {
            processed += driver.process(sample, context);
        }

        return processed;
    }

    private List<SpeakerDriverRuntime> getDriversFor(RadioAudioEmitter emitter) {
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

    private record MixContribution(float left, float right, float gain) {
    }

    private record EmitterKey(BlockPos pos, SpeakerType type) {
        private static EmitterKey from(RadioAudioEmitter emitter) {
            return new EmitterKey(emitter.pos(), emitter.type());
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
