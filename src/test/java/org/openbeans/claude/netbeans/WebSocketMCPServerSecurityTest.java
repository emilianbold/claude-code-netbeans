package org.openbeans.claude.netbeans;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the WebSocket server only accepts authenticated, non-browser
 * connections on the loopback interface.
 */
public class WebSocketMCPServerSecurityTest {

    private WebSocketMCPServer server;

    /** Handler that does not touch the NetBeans window system. */
    private static class NoUiHandler extends NetBeansMCPHandler {
        @Override
        void startTracking() {
            // no selection / diff tracking in tests
        }

        @Override
        void stopTracking() {
        }
    }

    @BeforeEach
    public void startServer() {
        server = new WebSocketMCPServer(new NoUiHandler());
        assertTrue(server.start(), "server should start");
    }

    @AfterEach
    public void stopServer() {
        server.stop();
    }

    private URI uri() {
        return URI.create("ws://127.0.0.1:" + server.getPort() + "/");
    }

    private WebSocket connect(String token, String origin) {
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (token != null) {
            builder.header(WebSocketMCPServer.AUTH_HEADER, token);
        }
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return builder.buildAsync(uri(), new WebSocket.Listener() {})
                .orTimeout(10, TimeUnit.SECONDS)
                .join();
    }

    @Test
    public void tokenIsGenerated() {
        assertNotNull(server.getAuthToken());
        assertFalse(server.getAuthToken().isBlank());
    }

    @Test
    public void acceptsConnectionWithValidToken() {
        WebSocket ws = connect(server.getAuthToken(), null);
        assertFalse(ws.isInputClosed());
        ws.abort();
    }

    @Test
    public void rejectsConnectionWithoutToken() {
        assertThrows(CompletionException.class, () -> connect(null, null));
    }

    @Test
    public void rejectsConnectionWithWrongToken() {
        assertThrows(CompletionException.class, () -> connect("not-the-token", null));
    }

    @Test
    public void rejectsBrowserOriginEvenWithValidToken() {
        assertThrows(CompletionException.class,
                () -> connect(server.getAuthToken(), "https://evil.example.com"));
    }

    @Test
    public void bindsToLoopbackOnly() {
        assertTrue(InetAddress.getLoopbackAddress().getHostAddress().equals(server.getHost())
                || "127.0.0.1".equals(server.getHost()),
                "server must bind to loopback, was: " + server.getHost());
    }
}
