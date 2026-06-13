package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

    @Test
    public void testHandleInitialize_withoutId_responseOmitsIdField() throws Exception {
        // Notification-style: no "id" in request
        String msg = "{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"params\":{}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNull(json.get("id"));
        assertNotNull(json.get("result"));
    }

    @Test
    public void testHandleToolsCall_unknownTool_returnsErrorContent() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nonexistent_tool\",\"arguments\":{}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        // Unknown tool name is caught inside handleToolsCall; error is returned as MCP text content
        String text = json.get("result").get("content").get(0).get("text").asText();
        assertTrue(text.startsWith("Error:"), "Expected error text, got: " + text);
    }

    @Test
    public void testHandleResourcesRead_unknownUri_returnsInternalError() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"unknown://something\"}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("error"));
        assertEquals(-32603, json.get("error").get("code").asInt());
    }

    @Test
    public void testHandleToolsList_containsAllExpectedToolNames() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\",\"params\":{}}";
        JsonNode tools = objectMapper.readTree(handler.handleMessage(objectMapper.readTree(msg)))
                .get("result").get("tools");
        Set<String> names = new HashSet<>();
        tools.forEach(t -> names.add(t.get("name").asText()));
        for (String expected : List.of("openFile", "getWorkspaceFolders", "getOpenEditors",
                "getCurrentSelection", "close_tab", "getDiagnostics",
                "checkDocumentDirty", "saveDocument", "closeAllDiffTabs", "openDiff")) {
            assertTrue(names.contains(expected), "Missing tool: " + expected);
        }
    }

    @Test
    public void testHandleResourcesList_returnsValidJson() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"resources/list\",\"params\":{}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertTrue(json.has("result") || json.has("error"), "Expected result or error");
    }

    @Test
    public void testHandleToolsCall_getDiagnostics_returnsEmptyArray() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"getDiagnostics\",\"arguments\":{}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertEquals("[]", json.get("result").asText());
    }

    @Test
    public void testHandleToolsCall_getCurrentSelection_returnsEmptySelection() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"getCurrentSelection\",\"arguments\":{}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        JsonNode result = json.get("result");
        assertTrue(result.get("isEmpty").asBoolean());
        assertEquals("", result.get("text").asText());
    }

    @Test
    public void testSetWebSocketSession_null_doesNotThrow() {
        // Exercises stopSelectionTracking() and stopDiffTabTracking() with nothing to clean up
        assertDoesNotThrow(() -> new NetBeansMCPHandler().setWebSocketSession(null));
    }

    @Test
    public void testSetWebSocketSession_nonNullThenNull_coversStartAndStopTracking() {
        // Calling with non-null → startSelectionTracking() + startDiffTabTracking() (adds listeners)
        // Calling with null → stopSelectionTracking() + stopDiffTabTracking() (removes listeners)
        Session mockSession = Mockito.mock(Session.class);
        NetBeansMCPHandler h = new NetBeansMCPHandler();
        assertDoesNotThrow(() -> h.setWebSocketSession(mockSession));
        assertDoesNotThrow(() -> h.setWebSocketSession(null));
    }

    @Test
    public void testHandleResourcesRead_projectUri_returnsInternalError() throws Exception {
        // project:// URI triggers getProjectInfo() -> FileUtil.toFileObject() returns null -> IAE
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"project:///nonexistent_xyz_abc\"}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("error"));
        assertEquals(-32603, json.get("error").get("code").asInt());
    }

    @Test
    public void testHandleToolsCall_closeTab_realRegistry_returnsTabClosed() throws Exception {
        // Exercises real findTopComponent() against empty NB registry
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"close_tab\",\"arguments\":{\"tab_name\":\"SomeFile.java\"}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("result"));
        String text = json.get("result").get("content").get(0).get("text").asText();
        assertEquals("TAB_CLOSED", text);
    }

    @Test
    public void testHandleToolsCall_closeAllDiffTabs_realRegistry_returnsClosed() throws Exception {
        // Exercises real closeAllDiffTabs() loop against empty NB registry
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"closeAllDiffTabs\",\"arguments\":{}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("result"));
        String text = json.get("result").get("content").get(0).get("text").asText();
        assertEquals("CLOSED_0_DIFF_TABS", text);
    }

    @Test
    public void testHandleToolsCall_checkDocumentDirty_outsideProjects_returnsResult() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":23,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"checkDocumentDirty\",\"arguments\":{\"filePath\":\"/tmp/test.java\"}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("result"));
        assertFalse(json.get("result").get("isDirty").asBoolean());
    }

    @Test
    public void testHandleToolsCall_getWorkspaceFolders_returnsValidJson() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":24,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"getWorkspaceFolders\",\"arguments\":{}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("result"));
    }

    @Test
    public void testHandleToolsCall_getOpenEditors_throughHandler_returnsEmptyEditors() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":25,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"getOpenEditors\",\"arguments\":{}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        JsonNode result = json.get("result");
        assertNotNull(result.get("editors"));
        assertTrue(result.get("editors").isArray());
    }

    @Test
    public void testHandleDiffTabClosed_noTrackedHandler_doesNothing() {
        // DiffTabTracker.remove("untracked") returns null -> method exits without calling sendResponse
        assertDoesNotThrow(() -> handler.handleDiffTabClosed("untracked-tab-xyz"));
    }

    @Test
    public void testHandleDiffTabClosed_withTrackedHandler_invokesHandler() {
        String tabName = "TestDiff-" + System.nanoTime();
        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.openbeans.claude.netbeans.tools.DiffTabTracker.register(tabName, result -> called.set(true));

        handler.handleDiffTabClosed(tabName);

        assertTrue(called.get(), "Handler should have been invoked with DIFF_REJECTED result");
    }

    @Test
    public void testSendAsyncToolResponse_nullSession_doesNotThrow() {
        // webSocketSession is null -> logs warning and returns early
        assertDoesNotThrow(() -> handler.sendAsyncToolResponse(1, "test result"));
    }

    @Test
    public void testSendAsyncToolResponse_closedSession_doesNotThrow() throws Exception {
        Session mockSession = Mockito.mock(Session.class);
        Mockito.when(mockSession.isOpen()).thenReturn(false);
        handler.setWebSocketSession(mockSession);
        // Session not open -> logs warning and returns early
        assertDoesNotThrow(() -> handler.sendAsyncToolResponse(2, "test result"));
        handler.setWebSocketSession(null);
    }

    @Test
    public void testSendAsyncToolResponse_openSession_sendsMessage() throws Exception {
        Session mockSession = Mockito.mock(Session.class);
        org.eclipse.jetty.websocket.api.RemoteEndpoint mockRemote =
                Mockito.mock(org.eclipse.jetty.websocket.api.RemoteEndpoint.class);
        Mockito.when(mockSession.isOpen()).thenReturn(true);
        Mockito.when(mockSession.getRemote()).thenReturn(mockRemote);

        handler.setWebSocketSession(mockSession);
        handler.sendAsyncToolResponse(42, "async result");

        Mockito.verify(mockRemote, Mockito.times(1)).sendString(Mockito.anyString());
        handler.setWebSocketSession(null);
    }

    @Test
    public void testHandleResourcesRead_projectUri_existingPath_returnsResultOrError() throws Exception {
        // /tmp exists on Linux; FileUtil.toFileObject may return a FileObject or null
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"project:///tmp\"}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        // Both paths are valid: success (result with path) or error (NB FileUtil unavailable)
        assertTrue(json.has("result") || json.has("error"));
        if (json.has("result")) {
            assertEquals("/tmp", json.get("result").get("path").asText());
        }
    }
}
