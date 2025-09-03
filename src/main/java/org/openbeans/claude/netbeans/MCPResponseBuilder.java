package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for building MCP protocol-compliant responses.
 * Ensures all responses follow the correct JSON-RPC 2.0 MCP schema.
 */
public class MCPResponseBuilder {
    
    private final ObjectMapper objectMapper;
    
    public MCPResponseBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Creates a standard MCP tool response with text content.
     * According to the MCP schema, tool responses must have:
     * { content: [ { type: "text", text: "..." } ] }
     * 
     * @param text The text content to include in the response
     * @return ObjectNode containing the properly formatted response
     */
    public ObjectNode createToolResponse(String text) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", text);
        content.add(textContent);
        
        result.set("content", content);
        return result;
    }
    
    /**
     * Creates a tool response with JSON content.
     * Serializes the object to JSON string and wraps it in the MCP format.
     * 
     * @param data The data object to include in the response
     * @return ObjectNode containing the properly formatted response
     */
    public ObjectNode createToolResponse(Object data) {
        try {
            String jsonText = objectMapper.writeValueAsString(data);
            return createToolResponse(jsonText);
        } catch (Exception e) {
            return createToolResponse("Error serializing response: " + e.getMessage());
        }
    }
    
    /**
     * Creates a tool response with multiple text segments.
     * 
     * @param texts Array of text segments to include
     * @return ObjectNode containing the properly formatted response
     */
    public ObjectNode createMultiPartToolResponse(String... texts) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        
        for (String text : texts) {
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", text);
            content.add(textContent);
        }
        
        result.set("content", content);
        return result;
    }
    
    /**
     * Creates an error response following JSON-RPC 2.0 specification.
     * 
     * @param id The request ID (can be null for notifications)
     * @param code The error code
     * @param message The error message
     * @param data Optional error data
     * @return String containing the JSON error response
     */
    public String createErrorResponse(Integer id, int code, String message, String data) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            
            // Only include id if it's not null (for requests, not notifications)
            if (id != null) {
                response.put("id", id);
            }
            
            ObjectNode error = objectMapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            if (data != null && !data.isEmpty()) {
                error.put("data", data);
            }
            response.set("error", error);
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            // Fallback error response if JSON creation fails
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }
    
    /**
     * Creates a notification message (no id field).
     * 
     * @param method The notification method name
     * @param params The notification parameters
     * @return ObjectNode containing the notification
     */
    public ObjectNode createNotification(String method, ObjectNode params) {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.set("params", params);
        }
        return notification;
    }
    
    /**
     * Creates a successful response with id.
     * 
     * @param id The request ID
     * @param result The result object
     * @return ObjectNode containing the response
     */
    public ObjectNode createResponse(int id, ObjectNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result);
        return response;
    }
    
    /**
     * Helper method to create empty ObjectNode.
     * 
     * @return Empty ObjectNode
     */
    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }
    
    /**
     * Helper method to create empty ArrayNode.
     * 
     * @return Empty ArrayNode
     */
    public ArrayNode arrayNode() {
        return objectMapper.createArrayNode();
    }
}