package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NetBeansMCPHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NetBeansMCPHandler handler = new NetBeansMCPHandler();

    @Test
    public void testHandleInitialize() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(1, json.get("id").asInt());
        JsonNode result = json.get("result");
        assertEquals("2024-11-05", result.get("protocolVersion").asText());
        assertEquals("netbeans-mcp-server", result.get("serverInfo").get("name").asText());
        assertNotNull(result.get("capabilities").get("tools"));
        assertNotNull(result.get("capabilities").get("resources"));
        assertNotNull(result.get("capabilities").get("prompts"));
    }

    @Test
    public void testHandleToolsList_count() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode tools = objectMapper.readTree(response).get("result").get("tools");
        assertTrue(tools.isArray());
        assertEquals(10, tools.size());
    }

    @Test
    public void testHandleToolsList_eachToolHasNameDescriptionAndSchema() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode tools = objectMapper.readTree(response).get("result").get("tools");
        for (JsonNode tool : tools) {
            String name = tool.get("name").asText();
            assertFalse(name.isEmpty(), "Tool name is empty");
            assertNotNull(tool.get("description"), "Tool missing description: " + name);
            assertNotNull(tool.get("inputSchema"), "Tool missing inputSchema: " + name);
        }
    }

    @Test
    public void testHandleUnknownMethod_returnsMethodNotFound() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"unknown/method\",\"params\":{}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("error"));
        assertEquals(-32601, json.get("error").get("code").asInt());
    }

    @Test
    public void testHandlePromptsList() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"prompts/list\",\"params\":{}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        JsonNode prompts = json.get("result").get("prompts");
        assertTrue(prompts.isArray());
        assertTrue(prompts.size() > 0);
        assertEquals("code_review", prompts.get(0).get("name").asText());
    }
}
