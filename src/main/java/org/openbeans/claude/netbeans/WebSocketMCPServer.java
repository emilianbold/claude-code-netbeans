package org.openbeans.claude.netbeans;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.ServerSocket;
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
    
    private Server server;
    private int port;
    private final NetBeansMCPHandler mcpHandler;
    
    public WebSocketMCPServer(NetBeansMCPHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
    }
    
    /**
     * Starts the WebSocket server on an available port.
     * 
     * @return true if server started successfully
     */
    public boolean start() {
        try {
            // Find an available port
            port = findAvailablePort();
            if (port == -1) {
                LOGGER.severe("No available ports found in range " + PORT_RANGE_START + "-" + PORT_RANGE_END);
                return false;
            }
            
            // Create Jetty server
            server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            server.addConnector(connector);
            
            // Setup WebSocket context
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);
            
            // Configure WebSocket
            JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
                // Configure WebSocket container
                wsContainer.setMaxTextMessageSize(1024 * 1024); // 1MB
                wsContainer.setMaxBinaryMessageSize(1024 * 1024); // 1MB
                wsContainer.setIdleTimeout(java.time.Duration.ZERO); // No timeout
                
                // Add WebSocket endpoint with subprotocol negotiation
                wsContainer.addMapping("/", (upgradeRequest, upgradeResponse) -> {
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
            
            // Start the server
            server.start();
            
            LOGGER.log(Level.INFO, "WebSocket MCP server started on port {0}", port);
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start WebSocket server", e);
            return false;
        }
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
    
    /**
     * Finds an available port in the specified range.
     * 
     * @return available port number, or -1 if none found
     */
    private int findAvailablePort() {
        for (int port = PORT_RANGE_START; port <= PORT_RANGE_END; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (Exception e) {
                // Port is in use, try next one
            }
        }
        return -1;
    }
}