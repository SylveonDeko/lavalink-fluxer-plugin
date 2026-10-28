package tech.mewdeko.lavalink.fluxer;

import com.sedmelluq.discord.lavaplayer.natives.opus.OpusDecoder;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.arbjerg.lavalink.api.IPlayer;
import dev.arbjerg.lavalink.api.ISocketContext;
import dev.arbjerg.lavalink.api.ISocketServer;
import org.springframework.beans.factory.DisposableBean;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Streams each Fluxer player's audio as PCM to the bot that owns it, one binary message per 20 ms frame.
 */
@Component
public class AudioStreamHandler extends AbstractWebSocketHandler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AudioStreamHandler.class);
    private static final int FRAME_MILLISECONDS = 20;
    private static final int SEND_TIME_LIMIT = 2000;
    private static final int BUFFER_SIZE_LIMIT = 1024 * 1024;

    private final ISocketServer socketServer;
    private final Map<Long, WebSocketSession> streams = new ConcurrentHashMap<>();
    private final Map<PlayerKey, PcmDecoder> decoders = new HashMap<>();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fluxer-audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        return thread;
    });

    public AudioStreamHandler(ISocketServer socketServer) {
        this.socketServer = socketServer;
        ticker.scheduleAtFixedRate(this::tick, FRAME_MILLISECONDS, FRAME_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws InterruptedException {
        ticker.shutdownNow();
        ticker.awaitTermination(1, TimeUnit.SECONDS);
        decoders.values().forEach(PcmDecoder::close);
        decoders.clear();
    }

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        long userId = (Long) session.getAttributes().get(AudioStreamConfiguration.USER_ID);
        WebSocketSession stream = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        WebSocketSession previous = streams.put(userId, stream);
        log.info("Audio stream opened for user {}", Long.toUnsignedString(userId));

        if (previous != null) {
            try {
                previous.close(CloseStatus.NORMAL.withReason("Replaced by a newer audio stream"));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        long userId = (Long) session.getAttributes().get(AudioStreamConfiguration.USER_ID);
        streams.computeIfPresent(userId, (id, stream) -> isSame(stream, session) ? null : stream);
        log.info("Audio stream closed for user {}: {}", Long.toUnsignedString(userId), status);
    }

    private static boolean isSame(WebSocketSession stream, WebSocketSession session) {
        return stream instanceof ConcurrentWebSocketSessionDecorator decorator
                ? decorator.getDelegate() == session
                : stream == session;
    }

    private void tick() {
        try {
            Set<PlayerKey> active = new HashSet<>();
            if (!streams.isEmpty()) {
                for (ISocketContext context : socketServer.getSessions().values()) {
                    WebSocketSession stream = streams.get(context.getUserId());
                    if (stream == null || !stream.isOpen()) {
                        continue;
                    }

                    for (IPlayer player : context.getPlayers().values()) {
                        PlayerKey key = new PlayerKey(context.getUserId(), player.getGuildId());
                        active.add(key);
                        send(stream, player, decoders.computeIfAbsent(key, k -> new PcmDecoder()));
                    }
                }
            }

            decoders.entrySet().removeIf(entry -> {
                if (active.contains(entry.getKey())) {
                    return false;
                }
                entry.getValue().close();
                return true;
            });
        } catch (Throwable e) {
            log.error("Audio stream tick failed", e);
        }
    }

    private static void send(WebSocketSession stream, IPlayer player, PcmDecoder decoder) {
        AudioFrame frame = player.getAudioPlayer().provide();
        if (frame == null) {
            return;
        }

        byte[] data = decoder.decode(frame.getData());
        if (data.length == 0) {
            return;
        }

        ByteBuffer message = ByteBuffer.allocate(Long.BYTES + data.length);
        message.putLong(player.getGuildId());
        message.put(data);
        message.flip();

        try {
            stream.sendMessage(new BinaryMessage(message));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropped an audio frame for guild {}", Long.toUnsignedString(player.getGuildId()), e);
        }
    }

    private record PlayerKey(long userId, long guildId) {
    }

    private static final class PcmDecoder implements AutoCloseable {

        private static final int SAMPLE_RATE = 48000;
        private static final int CHANNELS = 2;
        private static final int MAX_SAMPLES_PER_CHANNEL = SAMPLE_RATE * 120 / 1000;

        private final OpusDecoder decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
        private final ByteBuffer input = ByteBuffer.allocateDirect(4096);
        private final ShortBuffer output = ByteBuffer.allocateDirect(MAX_SAMPLES_PER_CHANNEL * CHANNELS * Short.BYTES)
                .order(ByteOrder.nativeOrder()).asShortBuffer();

        byte[] decode(byte[] opus) {
            input.clear();
            input.put(opus);
            input.flip();

            try {
                decoder.decode(input, output);
            } catch (IllegalStateException e) {
                log.debug("Could not decode an Opus frame", e);
                return new byte[0];
            }

            ByteBuffer pcm = ByteBuffer.allocate(output.remaining() * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            while (output.hasRemaining()) {
                pcm.putShort(output.get());
            }
            return pcm.array();
        }

        @Override
        public void close() {
            decoder.close();
        }
    }
}
