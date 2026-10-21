package tech.mewdeko.lavalink.fluxer;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
public class AudioStreamConfiguration implements WebSocketConfigurer {

    public static final String PATH = "/v4/fluxer/audio";
    static final String USER_ID = "fluxer.userId";

    private static final Logger log = LoggerFactory.getLogger(AudioStreamConfiguration.class);

    private final AudioStreamHandler handler;
    private final String password;

    public AudioStreamConfiguration(AudioStreamHandler handler, @Value("${lavalink.server.password}") String password) {
        this.handler = handler;
        this.password = password;
    }

    @Override
    public void registerWebSocketHandlers(@NotNull WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH).addInterceptors(new Handshake());
    }

    private final class Handshake implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response,
                                       @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) {
            if (!password.equals(request.getHeaders().getFirst("Authorization"))) {
                log.warn("Audio stream authorization failed from {}", request.getRemoteAddress());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            String userId = request.getHeaders().getFirst("User-Id");
            long parsed;
            try {
                parsed = userId == null ? 0 : Long.parseUnsignedLong(userId);
            } catch (NumberFormatException e) {
                parsed = 0;
            }

            if (parsed == 0) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }

            attributes.put(USER_ID, parsed);
            return true;
        }

        @Override
        public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response,
                                   @NotNull WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
