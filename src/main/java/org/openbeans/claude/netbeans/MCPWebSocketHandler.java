package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket handler for Model Context Protocol messages.
 * Handles the WebSocket connection and message routing between Claude Code and NetBeans.
 */
@WebSocket
public class MCPWebSocketHandler extends WebSocketAdapter {
    
    private static final Logger LOGGER = Logger.getLogger(MCPWebSocketHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NetBeansMCPHandler mcpHandler;
    
    public MCPWebSocketHandler(NetBeansMCPHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
    }
    
    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        LOGGER.log(Level.INFO, "Claude Code connected via WebSocket from {0}", 
                  session.getRemoteAddress());
        
        // Register this session with the MCP handler
        mcpHandler.setWebSocketSession(session);
    }
    
    @Override
    public void onWebSocketText(String message) {
        try {
            LOGGER.log(Level.FINE, "Received MCP message: {0}", message);
            
            // Parse the JSON-RPC message
            JsonNode messageNode = objectMapper.readTree(message);
            
            // Process the MCP message
            String response = mcpHandler.handleMessage(messageNode);
            
            // Send response back if there is one
            if (response != null && getSession() != null && getSession().isOpen()) {
                getSession().getRemote().sendString(response);
                LOGGER.log(Level.FINE, "Sent MCP response: {0}", response);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing WebSocket message: " + message, e);
            
            // Send error response
            try {
                String errorResponse = createErrorResponse(e.getMessage());
                if (getSession() != null && getSession().isOpen()) {
                    getSession().getRemote().sendString(errorResponse);
                }
            } catch (IOException ioException) {
                LOGGER.log(Level.SEVERE, "Failed to send error response", ioException);
            }
        }
    }
    
    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);
        LOGGER.log(Level.INFO, "Claude Code WebSocket connection closed: {0} - {1}", 
                  new Object[]{statusCode, reason});
        
        // Unregister this session
        mcpHandler.setWebSocketSession(null);
    }
    
    @Override
    public void onWebSocketError(Throwable cause) {
        super.onWebSocketError(cause);
        LOGGER.log(Level.WARNING, "WebSocket error occurred", cause);
    }
    
    /**
     * Creates a JSON-RPC error response.
     * 
     * @param errorMessage the error message
     * @return JSON error response string
     */
    private String createErrorResponse(String errorMessage) {
        try {
            return objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                    .put("jsonrpc", "2.0")
                    .put("id", (String) null)
                    .set("error", objectMapper.createObjectNode()
                        .put("code", -32603)
                        .put("message", "Internal error")
                        .put("data", errorMessage))
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create error response", e);
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }
}