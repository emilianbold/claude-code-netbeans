package org.openbeans.claude.netbeans;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket server for Model Context Protocol communication with Claude Code.
 * Handles the transport layer for MCP messages between Claude Code CLI and NetBeans.
 */
public class WebSocketMCPServer {
    
    private static final Logger LOGGER = Logger.getLogger(WebSocketMCPServer.class.getName());
    private static final int PORT_RANGE_START = 8990;
    private static final int PORT_RANGE_END = 9100;

    /** Header in which Claude Code sends the token from the lock file. */
    static final String AUTH_HEADER = "x-claude-code-ide-authorization";

    /** Only accept connections on the loopback interface. */
    private static final String BIND_HOST = "127.0.0.1";
    
    private Server server;
    private int port;
    private final NetBeansMCPHandler mcpHandler;
    private final String authToken = UUID.randomUUID().toString();
    
    public WebSocketMCPServer(NetBeansMCPHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
    }
    
    /**
     * Starts the WebSocket server on an available port.
     * 
     * @return true if server started successfully
     */
    public boolean start() {
        // Bind directly instead of probing first: a probed port could be taken
        // by another process before Jetty binds it.
        for (int candidate = PORT_RANGE_START; candidate <= PORT_RANGE_END; candidate++) {
            Server candidateServer = createServer(candidate);
            try {
                candidateServer.start();
                server = candidateServer;
                port = candidate;
                LOGGER.log(Level.INFO, "WebSocket MCP server started on port {0}", port);
                return true;
            } catch (Exception e) {
                stopQuietly(candidateServer);
                if (!isBindFailure(e)) {
                    LOGGER.log(Level.SEVERE, "Failed to start WebSocket server", e);
                    return false;
                }
                LOGGER.log(Level.FINE, "Port {0} is in use, trying next", candidate);
            }
        }
        LOGGER.severe("No available ports found in range " + PORT_RANGE_START + "-" + PORT_RANGE_END);
        return false;
    }

    private static boolean isBindFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BindException) {
                return true;
            }
        }
        return false;
    }

    private static void stopQuietly(Server s) {
        try {
            s.stop();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error stopping server after failed start", e);
        }
    }

    private Server createServer(int serverPort) {
        Server jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setHost(BIND_HOST);
        connector.setPort(serverPort);
        jetty.addConnector(connector);
            
        // Setup WebSocket context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jetty.setHandler(context);
        
        // Configure WebSocket
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            // Configure WebSocket container
            wsContainer.setMaxTextMessageSize(1024 * 1024); // 1MB
            wsContainer.setMaxBinaryMessageSize(1024 * 1024); // 1MB
            wsContainer.setIdleTimeout(java.time.Duration.ZERO); // No timeout
            
            // Add WebSocket endpoint with subprotocol negotiation
            wsContainer.addMapping("/", (upgradeRequest, upgradeResponse) -> {
                // Reject browsers: any web page can open a WebSocket to localhost,
                // but browsers always send an Origin header and the CLI does not.
                String origin = upgradeRequest.getHeader("Origin");
                if (origin != null) {
                    LOGGER.log(Level.WARNING, "Rejected WebSocket connection with Origin: {0}", origin);
                    reject(upgradeResponse, "Browser connections are not allowed");
                    return null;
                }

                // Require the auth token published in the lock file
                if (!isValidToken(upgradeRequest.getHeader(AUTH_HEADER))) {
                    LOGGER.warning("Rejected WebSocket connection with missing or invalid auth token");
                    reject(upgradeResponse, "Invalid or missing authentication token");
                    return null;
                }

                // Handle MCP subprotocol negotiation
                java.util.List<String> subprotocols = upgradeRequest.getSubProtocols();
                if (subprotocols != null) {
                    // Accept common MCP subprotocols
                    for (String subprotocol : subprotocols) {
                        if ("mcp".equals(subprotocol) || "mcp-v1".equals(subprotocol)) {
                            upgradeResponse.setAcceptedSubProtocol(subprotocol);
                            break;
                        }
                    }
                }
                return new MCPWebSocketHandler(mcpHandler);
            });
        });
        
        return jetty;
    }
    
    /**
     * Stops the WebSocket server.
     */
    public void stop() {
        if (server != null && server.isStarted()) {
            try {
                server.stop();
                LOGGER.info("WebSocket MCP server stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping WebSocket server", e);
            }
        }
    }
    
    /**
     * Gets the token clients must present in the {@value #AUTH_HEADER} header.
     *
     * @return the auth token for this server instance
     */
    public String getAuthToken() {
        return authToken;
    }

    /**
     * Gets the host the server is bound to.
     *
     * @return bind address
     */
    public String getHost() {
        return BIND_HOST;
    }

    private boolean isValidToken(String presented) {
        if (presented == null) {
            return false;
        }
        // Constant-time comparison to avoid leaking the token through timing
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                authToken.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse response, String message) {
        try {
            response.sendForbidden(message);
        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to send 403 response", e);
        }
    }
    
    /**
     * Gets the port the server is running on.
     * 
     * @return server port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Checks if the server is running.
     * 
     * @return true if server is started
     */
    public boolean isRunning() {
        return server != null && server.isStarted();
    }
    
}