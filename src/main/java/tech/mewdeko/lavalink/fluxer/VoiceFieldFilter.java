package tech.mewdeko.lavalink.fluxer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class VoiceFieldFilter extends OncePerRequestFilter {

    private static final String FLUXER_ENDPOINT = "fluxer";

    private static final Pattern PLAYER_PATH = Pattern.compile("^/v4/sessions/[^/]+/players/[^/]+$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        return !"PATCH".equalsIgnoreCase(request.getMethod())
                || !PLAYER_PATH.matcher(request.getRequestURI().substring(request.getContextPath().length())).matches();
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        JsonNode node;
        try {
            node = MAPPER.readTree(body);
        } catch (IOException e) {
            chain.doFilter(new BodyRequest(request, body), response);
            return;
        }

        if (node instanceof ObjectNode object && FLUXER_ENDPOINT.equals(object.path("voice").path("endpoint").asText(null))) {
            object.remove("voice");
            body = MAPPER.writeValueAsBytes(object);
        }

        chain.doFilter(new BodyRequest(request, body), response);
    }

    private static final class BodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getHeader(String name) {
            return "Content-Length".equalsIgnoreCase(name) ? String.valueOf(body.length) : super.getHeader(name);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return stream.read(buffer, offset, length);
                }
            };
        }
    }
}
