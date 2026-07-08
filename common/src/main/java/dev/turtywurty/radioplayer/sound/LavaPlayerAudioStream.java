package dev.turtywurty.radioplayer.sound;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.client.sounds.AudioStream;
import org.jspecify.annotations.NonNull;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LavaPlayerAudioStream implements AudioStream {
    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int FRAME_TIMEOUT_MS = 250;
    private static final int MAX_PLAYLIST_DEPTH = 3;

    private final AudioPlayer player;
    private final AudioFormat format;
    private byte[] pendingFrame;
    private int pendingFrameOffset;
    private boolean closed;

    public LavaPlayerAudioStream(String url) throws IOException {
        AudioPlayerManager playerManager = createPlayerManager(detectSampleRate(url));
        AudioTrack track = loadTrack(playerManager, url);
        this.player = playerManager.createPlayer();
        this.player.playTrack(track);
        AudioFrame firstFrame = provideFrame(10);
        if (firstFrame == null)
            throw new IOException("Timed out waiting for initial audio data from URL: " + url);

        this.format = createAudioFormat(firstFrame.getFormat());
        this.pendingFrame = firstFrame.getData();
    }

    public static void validate(String url) throws IOException {
        try (LavaPlayerAudioStream _ = new LavaPlayerAudioStream(url)) {
            // Opening the stream and receiving an initial frame proves Lavaplayer can play it.
        }
    }

    private static AudioPlayerManager createPlayerManager(int sampleRate) {
        var manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(new Pcm16AudioDataFormat(1, sampleRate, 960, false));
        manager.setFrameBufferDuration(1000);
        manager.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));
        return manager;
    }

    private static AudioTrack loadTrack(AudioPlayerManager playerManager, String url) throws IOException {
        AudioItem item = playerManager.loadItemSync(url);
        if (item instanceof AudioTrack track)
            return track;

        if (item instanceof AudioPlaylist playlist) {
            AudioTrack selectedTrack = playlist.getSelectedTrack();
            if (selectedTrack != null)
                return selectedTrack;

            List<AudioTrack> tracks = playlist.getTracks();
            if (!tracks.isEmpty())
                return tracks.getFirst();
        }

        throw new IOException("No playable audio stream found for URL: " + url);
    }

    private static int detectSampleRate(String url) {
        try {
            int sampleRate = detectSampleRate(URI.create(url), 0);
            return sampleRate > 0 ? sampleRate : DEFAULT_SAMPLE_RATE;
        } catch (Exception exception) {
            return DEFAULT_SAMPLE_RATE;
        }
    }

    private static int detectSampleRate(URI uri, int playlistDepth) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Icy-MetaData", "0");

        try (InputStream inputStream = connection.getInputStream()) {
            if (playlistDepth < MAX_PLAYLIST_DEPTH && isPlaylist(uri, connection)) {
                URI mediaUri = firstPlaylistMediaUri(uri, inputStream);
                return mediaUri == null ? -1 : detectSampleRate(mediaUri, playlistDepth + 1);
            }

            if (isMpegTs(uri, connection)) {
                int sampleRate = detectMpegTsSampleRate(inputStream);
                return sampleRate > 0 ? sampleRate : -1;
            }

            return detectMp3SampleRate(inputStream);
        }
    }

    private static boolean isPlaylist(URI uri, URLConnection connection) {
        String path = uri.getPath();
        String contentType = connection.getContentType();
        return path != null && path.endsWith(".m3u8") ||
                contentType != null && (contentType.contains("mpegurl") || contentType.contains("vnd.apple.mpegurl"));
    }

    private static boolean isMpegTs(URI uri, URLConnection connection) {
        String path = uri.getPath();
        String contentType = connection.getContentType();
        return path != null && path.endsWith(".ts") ||
                contentType != null && (contentType.contains("mp2t") || contentType.contains("mpegts"));
    }

    private static URI firstPlaylistMediaUri(URI playlistUri, InputStream inputStream) throws IOException {
        String playlist = new String(inputStream.readNBytes(1024 * 1024), StandardCharsets.UTF_8);
        for (String line : playlist.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#"))
                return playlistUri.resolve(trimmedLine);
        }

        return null;
    }

    private static int detectMp3SampleRate(InputStream inputStream) throws IOException {
        int previous = -1;
        int current;
        while ((current = inputStream.read()) != -1) {
            if (previous == 0xFF && (current & 0xE0) == 0xE0) {
                int third = inputStream.read();
                int fourth = inputStream.read();
                if (third == -1 || fourth == -1)
                    return -1;

                int header = 0xFFE00000 | (current << 16) | (third << 8) | fourth;
                int sampleRate = sampleRateFromMp3Header(header);
                if (sampleRate <= 0 && (current & 0xF0) == 0xF0) {
                    sampleRate = sampleRateFromAacHeader(current, third);
                }

                if (sampleRate > 0) {
                    return sampleRate;
                }
            }

            previous = current;
        }

        return -1;
    }

    private static int detectMpegTsSampleRate(InputStream inputStream) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        byte[] packet = new byte[188];
        int packetsRead = 0;
        int audioPid = -1;

        while (packetsRead++ < 256 && readFully(inputStream, packet)) {
            if (packet[0] != 0x47)
                continue;

            int pid = ((packet[1] & 0x1F) << 8) | (packet[2] & 0xFF);
            boolean payloadUnitStart = (packet[1] & 0x40) != 0;
            int adaptationFieldControl = (packet[3] >> 4) & 0b11;
            if (adaptationFieldControl == 0b00 || adaptationFieldControl == 0b10)
                continue;

            int payloadOffset = 4;
            if (adaptationFieldControl == 0b11) {
                payloadOffset += 1 + (packet[4] & 0xFF);
            }

            if (payloadOffset >= packet.length)
                continue;

            if (payloadUnitStart) {
                if (hasPesStartCode(packet, payloadOffset)) {
                    audioPid = pid;
                    int pesHeaderLength = packet[payloadOffset + 8] & 0xFF;
                    payloadOffset += 9 + pesHeaderLength;
                } else
                    continue;
            } else if (pid != audioPid)
                continue;

            if (payloadOffset < packet.length) {
                payload.write(packet, payloadOffset, packet.length - payloadOffset);
            }
        }

        return normalizeAacOutputSampleRate(detectAacSampleRate(payload.toByteArray()));
    }

    private static boolean hasPesStartCode(byte[] packet, int offset) {
        return offset <= packet.length - 9 &&
                packet[offset] == 0 &&
                packet[offset + 1] == 0 &&
                packet[offset + 2] == 1;
    }

    private static boolean readFully(InputStream inputStream, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = inputStream.read(buffer, offset, buffer.length - offset);
            if (read == -1)
                return false;

            offset += read;
        }

        return true;
    }

    private static int detectAacSampleRate(byte[] data) {
        for (int index = 0; index <= data.length - 7; index++) {
            int secondByte = data[index + 1] & 0xFF;
            int thirdByte = data[index + 2] & 0xFF;
            int sampleRate = sampleRateFromAacHeader(secondByte, thirdByte);
            if (sampleRate <= 0)
                continue;

            int frameLength = ((data[index + 3] & 0b11) << 11) |
                    ((data[index + 4] & 0xFF) << 3) |
                    ((data[index + 5] >> 5) & 0b111);
            if (frameLength < 7)
                continue;

            int nextFrameIndex = index + frameLength;
            if (nextFrameIndex > data.length - 2 ||
                    ((data[nextFrameIndex] & 0xFF) == 0xFF && (data[nextFrameIndex + 1] & 0xF0) == 0xF0))
                return sampleRate;
        }

        return -1;
    }

    private static int sampleRateFromMp3Header(int header) {
        int version = (header >> 19) & 0b11;
        int layer = (header >> 17) & 0b11;
        int bitrateIndex = (header >> 12) & 0b1111;
        int sampleRateIndex = (header >> 10) & 0b11;
        if (version == 0b01 || layer == 0 || bitrateIndex == 0 || bitrateIndex == 0b1111 || sampleRateIndex == 0b11)
            return -1;

        int sampleRate = switch (sampleRateIndex) {
            case 0 -> 44100;
            case 1 -> 48000;
            case 2 -> 32000;
            default -> -1;
        };

        return switch (version) {
            case 0b00 -> sampleRate / 4;
            case 0b10 -> sampleRate / 2;
            case 0b11 -> sampleRate;
            default -> -1;
        };
    }

    private static int sampleRateFromAacHeader(int secondByte, int thirdByte) {
        boolean validSync = (secondByte & 0b11110110) == 0b11110000;
        if (!validSync)
            return -1;

        int sampleRateIndex = (thirdByte >> 2) & 0b1111;
        return switch (sampleRateIndex) {
            case 0 -> 96000;
            case 1 -> 88200;
            case 2 -> 64000;
            case 3 -> 48000;
            case 4 -> 44100;
            case 5 -> 32000;
            case 6 -> 24000;
            case 7 -> 22050;
            case 8 -> 16000;
            case 9 -> 12000;
            case 10 -> 11025;
            case 11 -> 8000;
            case 12 -> 7350;
            default -> -1;
        };
    }

    private static int normalizeAacOutputSampleRate(int sampleRate) {
        if (sampleRate > 0 && sampleRate <= 24000)
            return sampleRate * 2;

        return sampleRate;
    }

    @Override
    public @NonNull AudioFormat getFormat() {
        return this.format;
    }

    @Override
    public @NonNull ByteBuffer read(int expectedSize) throws IOException {
        ByteBuffer output = BufferUtils.createByteBuffer(expectedSize);

        while (!this.closed && output.hasRemaining()) {
            if (this.pendingFrame != null) {
                copyPendingFrame(output);
                continue;
            }

            AudioFrame frame = provideFrame(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (frame == null) {
                writeSilence(output);
                continue;
            }

            this.pendingFrame = frame.getData();
            this.pendingFrameOffset = 0;
        }

        output.flip();
        return output;
    }

    private AudioFrame provideFrame(int timeoutSeconds) throws IOException {
        return provideFrame(timeoutSeconds, TimeUnit.SECONDS);
    }

    private AudioFrame provideFrame(long timeout, TimeUnit unit) throws IOException {
        try {
            return this.player.provide(timeout, unit);
        } catch (TimeoutException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for audio data", exception);
        }
    }

    private static void writeSilence(ByteBuffer output) {
        while (output.hasRemaining()) {
            output.put((byte) 0);
        }
    }

    private static AudioFormat createAudioFormat(AudioDataFormat format) {
        return new AudioFormat(format.sampleRate, 16, format.channelCount, true, false);
    }

    private void copyPendingFrame(ByteBuffer output) {
        int bytesToCopy = Math.min(output.remaining(), this.pendingFrame.length - this.pendingFrameOffset);
        output.put(this.pendingFrame, this.pendingFrameOffset, bytesToCopy);
        this.pendingFrameOffset += bytesToCopy;

        if (this.pendingFrameOffset >= this.pendingFrame.length) {
            this.pendingFrame = null;
            this.pendingFrameOffset = 0;
        }
    }

    @Override
    public void close() {
        this.closed = true;
        this.player.destroy();
    }
}
