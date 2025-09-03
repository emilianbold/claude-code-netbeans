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
    private final MCPResponseBuilder responseBuilder;
    
    public MCPWebSocketHandler(NetBeansMCPHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
        this.responseBuilder = new MCPResponseBuilder(objectMapper);
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
            
            // Extract ID if present (for error responses)
            Integer requestId = null;
            if (messageNode.has("id") && !messageNode.get("id").isNull()) {
                requestId = messageNode.get("id").asInt();
            }
            
            // Process the MCP message
            String response = mcpHandler.handleMessage(messageNode);
            
            // Send response back if there is one
            if (response != null && getSession() != null && getSession().isOpen()) {
                getSession().getRemote().sendString(response);
                LOGGER.log(Level.FINE, "Sent MCP response: {0}", response);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing WebSocket message: " + message, e);
            
            // Try to extract requestId from the original message for error response
            Integer errorRequestId = null;
            try {
                JsonNode messageNode = objectMapper.readTree(message);
                if (messageNode.has("id") && !messageNode.get("id").isNull()) {
                    errorRequestId = messageNode.get("id").asInt();
                }
            } catch (Exception parseEx) {
                // Ignore parsing errors, we'll send error without ID
            }
            
            // Send error response with proper ID handling
            sendErrorResponse(errorRequestId, e.getMessage());
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
     * Sends an error response with proper ID handling.
     * 
     * @param requestId the request ID (can be null)
     * @param errorMessage the error message
     */
    private void sendErrorResponse(Integer requestId, String errorMessage) {
        try {
            String errorResponse = responseBuilder.createErrorResponse(
                requestId, -32603, "Internal error", errorMessage
            );
            if (getSession() != null && getSession().isOpen()) {
                getSession().getRemote().sendString(errorResponse);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send error response", e);
        }
    }
}