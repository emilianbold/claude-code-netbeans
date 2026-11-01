package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

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
        mcpHandler.sessionOpened(session);
    }
    
    @Override
    public void onWebSocketText(String message) {
        LOGGER.log(Level.FINE, "Received MCP message: {0}", message);

        JsonNode messageNode;
        try {
            messageNode = objectMapper.readTree(message);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Received invalid JSON: {0}", e.getMessage());
            sendText(responseBuilder.createErrorResponse(null, MCPResponseBuilder.PARSE_ERROR, "Parse error", e.getMessage()));
            return;
        }
        if (messageNode == null || !messageNode.isObject()) {
            sendText(responseBuilder.createErrorResponse(null, MCPResponseBuilder.INVALID_REQUEST, "Invalid Request", "Expected a JSON object"));
            return;
        }

        // handleMessage never throws and returns null when no response is due
        Session session = getSession();
        String response = mcpHandler.handleMessage(messageNode,
                asyncResponse -> NetBeansMCPHandler.sendTo(session, asyncResponse));
        if (response != null) {
            sendText(response);
            LOGGER.log(Level.FINE, "Sent MCP response: {0}", response);
        }
    }

    private void sendText(String text) {
        NetBeansMCPHandler.sendNow(getSession(), text);
    }
    
    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        Session session = getSession();
        super.onWebSocketClose(statusCode, reason);
        LOGGER.log(Level.INFO, "Claude Code WebSocket connection closed: {0} - {1}", 
                  new Object[]{statusCode, reason});
        
        // Unregister this session
        if (session != null) {
            mcpHandler.sessionClosed(session);
        }
    }
    
    @Override
    public void onWebSocketError(Throwable cause) {
        super.onWebSocketError(cause);
        LOGGER.log(Level.WARNING, "WebSocket error occurred", cause);
    }
    
}