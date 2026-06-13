package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MCPResponseBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MCPResponseBuilder builder = new MCPResponseBuilder(objectMapper);

    @Test
    public void testCreateToolResponse_text() throws Exception {
        ObjectNode result = builder.createToolResponse("hello");
        assertEquals(
            "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}",
            objectMapper.writeValueAsString(result)
        );
    }

    @Test
    public void testCreateToolResponse_objectString_returnsTextNode() {
        JsonNode result = builder.createToolResponse((Object) "world");
        assertTrue(result.isTextual());
        assertEquals("world", result.asText());
    }

    @Test
    public void testCreateToolResponse_objectPojo_returnsObjectNode() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("key", "value");
        JsonNode result = builder.createToolResponse((Object) data);
        assertFalse(result.isTextual());
        assertEquals("value", result.get("key").asText());
    }

    @Test
    public void testCreateMultiPartToolResponse() throws Exception {
        ObjectNode result = builder.createMultiPartToolResponse("part1", "part2");
        JsonNode content = result.get("content");
        assertEquals(2, content.size());
        assertEquals("part1", content.get(0).get("text").asText());
        assertEquals("part2", content.get(1).get("text").asText());
    }

    @Test
    public void testCreateErrorResponse_withData() {
        String response = builder.createErrorResponse(42, -32601, "Method not found", "details");
        assertTrue(response.contains("\"id\":42"));
        assertTrue(response.contains("-32601"));
        assertTrue(response.contains("Method not found"));
        assertTrue(response.contains("details"));
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""));
    }

    @Test
    public void testCreateErrorResponse_nullId_nullData() {
        String response = builder.createErrorResponse(null, -32603, "Internal error", null);
        assertFalse(response.contains("\"id\""));
        assertTrue(response.contains("-32603"));
        assertFalse(response.contains("\"data\""));
    }

    @Test
    public void testCreateErrorResponse_emptyData_omitsDataField() {
        String response = builder.createErrorResponse(1, -32600, "Bad request", "");
        assertFalse(response.contains("\"data\""));
    }

    @Test
    public void testCreateNotification_withParams() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("key", "value");
        ObjectNode result = builder.createNotification("selection_changed", params);
        assertEquals("2.0", result.get("jsonrpc").asText());
        assertEquals("selection_changed", result.get("method").asText());
        assertEquals("value", result.get("params").get("key").asText());
    }

    @Test
    public void testCreateNotification_nullParams() {
        ObjectNode result = builder.createNotification("notifications/initialized", null);
        assertEquals("2.0", result.get("jsonrpc").asText());
        assertEquals("notifications/initialized", result.get("method").asText());
        assertNull(result.get("params"));
    }

    @Test
    public void testCreateResponse() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("foo", "bar");
        ObjectNode response = builder.createResponse(99, data);
        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(99, response.get("id").asInt());
        assertEquals("bar", response.get("result").get("foo").asText());
    }
}
