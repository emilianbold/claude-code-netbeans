package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openbeans.claude.netbeans.tools.params.CloseTabParams;

public class CloseTabTest {

    ObjectMapper objectMapper = new ObjectMapper();
    MCPResponseBuilder responseBuilder = new MCPResponseBuilder(objectMapper);

    @Test
    public void testJSONResponse() throws JsonProcessingException, Exception {
        CloseTab tool = new CloseTab() {
            @Override
            boolean closeTopComponent(String tabName) {
                return true;
            }
        };

        JsonNode n = responseBuilder.createToolResponse(tool.run(new CloseTabParams("a name")));

        // Note the full JSON response is: { "result" : n, "jsonrpc" : "2.0","id" : <someID> }
        assertEquals("{\"content\":[{\"type\":\"text\",\"text\":\"TAB_CLOSED\"}]}", objectMapper.writeValueAsString(n));
    }

    @Test
    public void testJSONResponse_tabNotFound() throws Exception {
        CloseTab tool = new CloseTab() {
            @Override
            boolean closeTopComponent(String tabName) {
                return false;
            }
        };

        JsonNode n = responseBuilder.createToolResponse(tool.run(new CloseTabParams("missing tab")));

        // TAB_CLOSED is returned even when the tab is not found (user may have already closed it)
        assertEquals("{\"content\":[{\"type\":\"text\",\"text\":\"TAB_CLOSED\"}]}", objectMapper.writeValueAsString(n));
    }

    @Test
    public void parseArguments_deserializesTabName() {
        CloseTab tool = new CloseTab();
        ObjectNode json = objectMapper.createObjectNode();
        json.put("tab_name", "MyFile.java");  // schema generates snake_case JSON property
        CloseTabParams params = tool.parseArguments(json);
        assertEquals("MyFile.java", params.getTabName());
    }

    @Test
    public void closeTopComponent_returnsFalse_withEmptyRegistry() {
        // Exercises findTopComponent() with real (empty) NB TopComponent registry
        assertFalse(new CloseTab().closeTopComponent("NonExistentTab.java"));
    }

    private CloseTab tabToolWith(TopComponent... tcs) {
        return new CloseTab() {
            @Override
            protected Set<TopComponent> getOpenTopComponents() {
                return Set.of(tcs);
            }
        };
    }

    @Test
    public void closeTopComponent_exactDisplayNameMatch_closesAndReturnsTrue() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("MyFile.java");

        CloseTab tool = tabToolWith(mockTC);
        assertTrue(tool.closeTopComponent("MyFile.java"));
        verify(mockTC).close();
    }

    @Test
    public void closeTopComponent_noDisplayNameMatch_nullNodes_returnsFalse() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("OtherFile.java");
        // getActivatedNodes() returns null by Mockito default → second loop: nodes null → skip

        CloseTab tool = tabToolWith(mockTC);
        assertFalse(tool.closeTopComponent("MyFile.java"));
    }

    @Test
    public void closeTopComponent_noDisplayNameMatch_nullDataObject_returnsFalse() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockTC.getDisplayName()).thenReturn("OtherFile.java");
        when(mockTC.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(null);

        CloseTab tool = tabToolWith(mockTC);
        assertFalse(tool.closeTopComponent("MyFile.java"));
    }

    @Test
    public void getDescription_returnsNonBlank() {
        assertFalse(new CloseTab().getDescription().isBlank());
    }

    private CloseTab tabToolWithDataObject(TopComponent tc, String fileName, String ext) {
        DataObject mockDO = Mockito.mock(DataObject.class);
        FileObject mockFO = Mockito.mock(FileObject.class);
        Node mockNode = Mockito.mock(Node.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(tc.getActivatedNodes()).thenReturn(new Node[]{mockNode});
        when(mockNode.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(DataObject.class)).thenReturn(mockDO);
        when(mockDO.getPrimaryFile()).thenReturn(mockFO);
        when(mockFO.getName()).thenReturn(fileName);
        when(mockFO.getExt()).thenReturn(ext);
        return tabToolWith(tc);
    }

    @Test
    public void closeTopComponent_dataObjectFullNameMatch_closesAndReturnsTrue() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("OtherFile.txt"); // no displayName match
        CloseTab tool = tabToolWithDataObject(mockTC, "MyFile", "java");

        assertTrue(tool.closeTopComponent("MyFile.java")); // matches fileName + "." + ext
        verify(mockTC).close();
    }

    @Test
    public void closeTopComponent_dataObjectNameOnlyMatch_closesAndReturnsTrue() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("OtherFile.txt");
        CloseTab tool = tabToolWithDataObject(mockTC, "MyFile", "java");

        assertTrue(tool.closeTopComponent("MyFile")); // matches fileName alone
        verify(mockTC).close();
    }

    @Test
    public void closeTopComponent_dataObjectNoMatch_returnsFalse() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("OtherFile.txt");
        CloseTab tool = tabToolWithDataObject(mockTC, "WrongFile", "java");

        assertFalse(tool.closeTopComponent("MyFile.java"));
        verify(mockTC, never()).close();
    }

    @Test
    public void run_closeTabThrows_wrapsInRuntimeException() throws Exception {
        CloseTab tool = new CloseTab() {
            @Override
            boolean closeTopComponent(String tabName) {
                throw new RuntimeException("simulated close failure");
            }
        };
        assertThrows(RuntimeException.class, () -> tool.run(new CloseTabParams("someTab")));
    }
}
