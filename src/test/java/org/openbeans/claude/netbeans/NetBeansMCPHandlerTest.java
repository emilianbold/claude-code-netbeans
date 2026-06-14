package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.text.JTextComponent;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
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
    public void testTrackEditorSelection_nullTc_exceptionCaughtGracefully() {
        // Passing null → NPE on tc.getActivatedNodes() → caught by internal try-catch
        assertDoesNotThrow(() -> handler.trackEditorSelection(null));
    }

    @Test
    public void testTrackEditorSelection_mockTcWithNullNodes_noOp() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Mockito.when(mockTC.getActivatedNodes()).thenReturn(null);

        assertDoesNotThrow(() -> handler.trackEditorSelection(mockTC));
    }

    @Test
    public void testTrackEditorSelection_mockTcWithEmptyNodes_noOp() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Mockito.when(mockTC.getActivatedNodes()).thenReturn(new Node[0]);

        assertDoesNotThrow(() -> handler.trackEditorSelection(mockTC));
    }

    @Test
    public void testTrackEditorSelection_nodeWithNullEditorCookie_noOp() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(null);

        assertDoesNotThrow(() -> handler.trackEditorSelection(mockTC));
    }

    @Test
    public void testTrackEditorSelection_editorCookieWithNullPanes_noOp() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);
        when(mockCookie.getOpenedPanes()).thenReturn(null);

        assertDoesNotThrow(() -> handler.trackEditorSelection(mockTC));
    }

    @Test
    public void testTrackEditorSelection_editorCookieWithEmptyPanes_noOp() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);
        when(mockCookie.getOpenedPanes()).thenReturn(new javax.swing.JEditorPane[0]);

        assertDoesNotThrow(() -> handler.trackEditorSelection(mockTC));
    }

    @Test
    public void testTrackEditorSelection_withOpenPane_registersListenerAndSendsEvent() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        javax.swing.JEditorPane mockPane = Mockito.mock(javax.swing.JEditorPane.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);
        when(mockCookie.getOpenedPanes()).thenReturn(new javax.swing.JEditorPane[]{mockPane});

        // webSocketSession is null → sendSelectionChangeEvent returns early
        assertDoesNotThrow(() -> handler.trackEditorSelection(mockTC));
        // Verify listener was added to the pane
        verify(mockPane).addCaretListener(any());
    }

    @Test
    public void testSendSelectionChangeEvent_nullSession_returnsEarly() {
        // webSocketSession is null on a fresh handler → early return without exception
        assertDoesNotThrow(() -> handler.sendSelectionChangeEvent(null, null));
    }

    @Test
    public void testSendSelectionChangeEvent_closedSession_returnsEarly() throws Exception {
        Session mockSession = Mockito.mock(Session.class);
        Mockito.when(mockSession.isOpen()).thenReturn(false);
        handler.setWebSocketSession(mockSession);

        assertDoesNotThrow(() -> handler.sendSelectionChangeEvent(null, null));

        handler.setWebSocketSession(null);
    }

    @Test
    public void testSendSelectionChangeEvent_openSession_nullTextComponent_exceptionCaught() throws Exception {
        Session mockSession = Mockito.mock(Session.class);
        org.eclipse.jetty.websocket.api.RemoteEndpoint mockRemote =
                Mockito.mock(org.eclipse.jetty.websocket.api.RemoteEndpoint.class);
        Mockito.when(mockSession.isOpen()).thenReturn(true);
        Mockito.when(mockSession.getRemote()).thenReturn(mockRemote);
        handler.setWebSocketSession(mockSession);

        // null textComponent → NPE on textComponent.getSelectedText() → caught internally
        assertDoesNotThrow(() -> handler.sendSelectionChangeEvent(null, null));

        handler.setWebSocketSession(null);
    }

    @Test
    public void testTrackEditorSelection_samePaneTwice_secondCallIsNoOp() {
        // Second call with the same pane: textComponent == currentTextComponent → inner if skipped
        NetBeansMCPHandler h = new NetBeansMCPHandler();
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        javax.swing.JEditorPane mockPane = Mockito.mock(javax.swing.JEditorPane.class);
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);
        when(mockCookie.getOpenedPanes()).thenReturn(new javax.swing.JEditorPane[]{mockPane});

        h.trackEditorSelection(mockTC);  // first call sets currentTextComponent = mockPane
        h.trackEditorSelection(mockTC);  // second call: same pane → no-op

        verify(mockPane, times(1)).addCaretListener(any());
    }

    @Test
    public void testTrackEditorSelection_secondDifferentPane_removesOldListenerAddsNew() {
        // Covers the currentTextComponent != null branch (remove old listener)
        NetBeansMCPHandler h = new NetBeansMCPHandler();
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        javax.swing.JEditorPane pane1 = Mockito.mock(javax.swing.JEditorPane.class);
        javax.swing.JEditorPane pane2 = Mockito.mock(javax.swing.JEditorPane.class);
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);
        when(mockCookie.getOpenedPanes()).thenReturn(new javax.swing.JEditorPane[]{pane1});

        h.trackEditorSelection(mockTC);  // sets currentTextComponent = pane1, registers listener

        when(mockCookie.getOpenedPanes()).thenReturn(new javax.swing.JEditorPane[]{pane2});
        h.trackEditorSelection(mockTC);  // currentTextComponent != null → removes from pane1, adds to pane2

        verify(pane1).removeCaretListener(any());
        verify(pane2).addCaretListener(any());
    }

    @Test
    public void testSendSelectionChangeEvent_openSession_mockComponents_nonStyledDoc_noSend() throws Exception {
        // Covers getSelectedText/getSelectionStart/End/getDocument/DataObject lookup;
        // doc is not StyledDocument → condition false → no WebSocket message sent
        Session mockSession = Mockito.mock(Session.class);
        org.eclipse.jetty.websocket.api.RemoteEndpoint mockRemote =
                Mockito.mock(org.eclipse.jetty.websocket.api.RemoteEndpoint.class);
        JTextComponent mockText = Mockito.mock(JTextComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockSession.isOpen()).thenReturn(true);
        when(mockSession.getRemote()).thenReturn(mockRemote);
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(null);
        // textComponent.getDocument() returns null (Mockito default) → not StyledDocument

        handler.setWebSocketSession(mockSession);
        assertDoesNotThrow(() -> handler.sendSelectionChangeEvent(mockText, mockNode));
        // No message should be sent since doc is not StyledDocument
        verify(mockRemote, never()).sendString(anyString());
        handler.setWebSocketSession(null);
    }

    @Test
    public void testSendSelectionChangeEvent_styledDocAndDataObject_nullFileObject_skipsFilePath() throws Exception {
        // Covers: doc instanceof StyledDocument path + dataObject != null path;
        // getPrimaryFile() returns null → if(fileObject != null) is false → no FileUtil call
        Session mockSession = Mockito.mock(Session.class);
        JTextComponent mockText = Mockito.mock(JTextComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        DataObject mockDO = Mockito.mock(DataObject.class);
        javax.swing.text.StyledDocument mockDoc = Mockito.mock(javax.swing.text.StyledDocument.class);

        when(mockSession.isOpen()).thenReturn(true);
        when(mockSession.getRemote()).thenReturn(Mockito.mock(org.eclipse.jetty.websocket.api.RemoteEndpoint.class));
        when(mockText.getDocument()).thenReturn(mockDoc);
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(mockDO);
        when(mockDO.getPrimaryFile()).thenReturn(null);  // null FO → inner if skipped

        handler.setWebSocketSession(mockSession);
        assertDoesNotThrow(() -> handler.sendSelectionChangeEvent(mockText, mockNode));
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

    @Test
    public void testHandleInitialize_withOpenSession_sendsInitializedNotification() throws Exception {
        // Set up an open session before calling initialize so sendInitializedNotification fires
        Session mockSession = Mockito.mock(Session.class);
        org.eclipse.jetty.websocket.api.RemoteEndpoint mockRemote =
                Mockito.mock(org.eclipse.jetty.websocket.api.RemoteEndpoint.class);
        Mockito.when(mockSession.isOpen()).thenReturn(true);
        Mockito.when(mockSession.getRemote()).thenReturn(mockRemote);

        NetBeansMCPHandler h = new NetBeansMCPHandler();
        h.setWebSocketSession(mockSession);

        String msg = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        String response = h.handleMessage(objectMapper.readTree(msg));

        // The initialize response itself is returned
        assertNotNull(response);
        JsonNode json = objectMapper.readTree(response);
        assertEquals("2024-11-05", json.get("result").get("protocolVersion").asText());

        // Verify sendString was called (once for the initialized notification)
        Mockito.verify(mockRemote, Mockito.atLeastOnce()).sendString(Mockito.anyString());

        h.setWebSocketSession(null);
    }

    @Test
    public void testHandleToolsCall_openDiff_returnsNull_asyncTool() throws Exception {
        // openDiff returns AsyncResponse → handleToolsCall returns null → handleMessage returns null
        // Use empty arguments so parseArguments succeeds; run() returns AsyncResponse even on error
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"openDiff\",\"arguments\":{}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        // Async tool: handleMessage returns null when handleToolsCall returns null
        assertNull(response);
    }

    @Test
    public void testHandleMessage_missingMethodField_returnsError() throws Exception {
        // message.get("method") returns null → .asText() throws NPE → caught by catch(Exception e)
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":99}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        assertNotNull(response);
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("error"));
        assertEquals(-32603, json.get("error").get("code").asInt());
    }

    @Test
    public void testHandleToolsCall_openFile_outsideProjects_returnsError() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"openFile\",\"arguments\":{\"filePath\":\"/tmp/test.java\"}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("result"));
        String text = json.get("result").get("content").get(0).get("text").asText();
        assertTrue(text.startsWith("Error:"), "Expected error text, got: " + text);
    }

    @Test
    public void testHandleToolsCall_saveDocument_outsideProjects_returnsError() throws Exception {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"saveDocument\",\"arguments\":{\"filePath\":\"/tmp/test.java\"}}}";
        String response = handler.handleMessage(objectMapper.readTree(msg));
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("result"));
        String text = json.get("result").get("content").get(0).get("text").asText();
        assertTrue(text.startsWith("Error:"), "Expected error text, got: " + text);
    }

    @Test
    public void testHandleInitialize_sendStringThrows_sendExceptionCaught() throws Exception {
        // Makes sendString throw → sendInitializedNotification catch block is covered
        Session mockSession = Mockito.mock(Session.class);
        org.eclipse.jetty.websocket.api.RemoteEndpoint mockRemote =
                Mockito.mock(org.eclipse.jetty.websocket.api.RemoteEndpoint.class);
        Mockito.when(mockSession.isOpen()).thenReturn(true);
        Mockito.when(mockSession.getRemote()).thenReturn(mockRemote);
        Mockito.doThrow(new RuntimeException("send failed")).when(mockRemote).sendString(Mockito.anyString());

        NetBeansMCPHandler h = new NetBeansMCPHandler();
        h.setWebSocketSession(mockSession);

        String msg = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        // sendInitializedNotification catches the RuntimeException internally — initialize still succeeds
        String response = h.handleMessage(objectMapper.readTree(msg));
        assertNotNull(response);
        JsonNode json = objectMapper.readTree(response);
        assertEquals("2024-11-05", json.get("result").get("protocolVersion").asText());
        h.setWebSocketSession(null);
    }

    @Test
    public void testSendAsyncToolResponse_sendStringThrows_exceptionCaught() throws Exception {
        // Makes sendString throw → sendAsyncToolResponse catch block is covered
        Session mockSession = Mockito.mock(Session.class);
        org.eclipse.jetty.websocket.api.RemoteEndpoint mockRemote =
                Mockito.mock(org.eclipse.jetty.websocket.api.RemoteEndpoint.class);
        Mockito.when(mockSession.isOpen()).thenReturn(true);
        Mockito.when(mockSession.getRemote()).thenReturn(mockRemote);
        Mockito.doThrow(new RuntimeException("send failed")).when(mockRemote).sendString(Mockito.anyString());

        handler.setWebSocketSession(mockSession);
        assertDoesNotThrow(() -> handler.sendAsyncToolResponse(1, "result"));
        handler.setWebSocketSession(null);
    }
}
