package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for building MCP protocol-compliant responses.
 * Ensures all responses follow the correct JSON-RPC 2.0 MCP schema.
 */
public class MCPResponseBuilder {

    /** JSON-RPC 2.0 error codes. */
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    private final ObjectMapper objectMapper;

    public MCPResponseBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an MCP tool result with a single text content item:
     * { content: [ { type: "text", text: "..." } ] }
     *
     * @param text The text content to include in the response
     * @return ObjectNode containing the properly formatted response
     */
    public ObjectNode createToolResponse(String text) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode textContent = content.addObject();
        textContent.put("type", "text");
        textContent.put("text", text);
        return result;
    }

    /**
     * Converts any tool return value into an MCP tool result.
     * Values that already have a {@code content} array are passed through;
     * strings become a text item; anything else is serialized to JSON text.
     *
     * @param data the value returned by a tool
     * @return a CallToolResult object
     */
    public ObjectNode createToolResponse(Object data) {
        if (data == null) {
            return createToolResponse("");
        }
        if (data instanceof String) {
            return createToolResponse((String) data);
        }
        JsonNode tree = objectMapper.valueToTree(data);
        if (tree.isObject() && tree.path("content").isArray()) {
            return (ObjectNode) tree;
        }
        try {
            return createToolResponse(objectMapper.writeValueAsString(tree));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize tool result", e);
        }
    }

    /**
     * Creates a tool result reporting a failed tool execution.
     *
     * @param message error message shown to the model
     * @return a CallToolResult with isError set
     */
    public ObjectNode createToolErrorResponse(String message) {
        ObjectNode result = createToolResponse(message);
        result.put("isError", true);
        return result;
    }

    /**
     * Creates an error response following JSON-RPC 2.0 specification.
     *
     * @param id The request ID, or null if it could not be determined
     *           (serialized as {@code "id": null} as the spec requires)
     * @param code The error code
     * @param message The error message
     * @param data Optional error data
     * @return String containing the JSON error response
     */
    public String createErrorResponse(JsonNode id, int code, String message, String data) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id != null ? id : NullNode.getInstance());

            ObjectNode error = response.putObject("error");
            error.put("code", code);
            error.put("message", message);
            if (data != null && !data.isEmpty()) {
                error.put("data", data);
            }
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            // Fallback error response if JSON creation fails
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
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
     * Creates a successful response, echoing the request id verbatim.
     *
     * @param id The request ID (number or string)
     * @param result The result object
     * @return ObjectNode containing the response
     */
    public ObjectNode createResponse(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
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
