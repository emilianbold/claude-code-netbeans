package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.tools.params.CloseAllDiffTabsParams;
import org.junit.jupiter.api.Test;
import org.openide.windows.TopComponent;
import static org.junit.jupiter.api.Assertions.*;

public class CloseAllDiffTabsTest {

    ObjectMapper objectMapper = new ObjectMapper();
    MCPResponseBuilder responseBuilder = new MCPResponseBuilder(objectMapper);

    @Test
    public void testJSONResponse() throws JsonProcessingException {
        CloseAllDiffTabs tool = new CloseAllDiffTabs() {
            @Override
            int closeAllDiffTabs() {
                return 1;
            }

        };

        JsonNode n = responseBuilder.createToolResponse(tool.run(new CloseAllDiffTabsParams()));

        // Note the full JSON response is: { "result" : n, "jsonrpc" : "2.0","id" : <someID> }
        assertEquals("{\"content\":[{\"type\":\"text\",\"text\":\"CLOSED_1_DIFF_TABS\"}]}", objectMapper.writeValueAsString(n));
    }

    @Test
    public void testJSONResponse_noTabsClosed() throws JsonProcessingException {
        CloseAllDiffTabs tool = new CloseAllDiffTabs() {
            @Override
            int closeAllDiffTabs() {
                return 0;
            }
        };

        JsonNode n = responseBuilder.createToolResponse(tool.run(new CloseAllDiffTabsParams()));

        assertEquals("{\"content\":[{\"type\":\"text\",\"text\":\"CLOSED_0_DIFF_TABS\"}]}", objectMapper.writeValueAsString(n));
    }

    @Test
    public void closeAllDiffTabs_direct_returnsZero_withEmptyRegistry() {
        // Exercises real closeAllDiffTabs() loop with NB registry returning empty set
        assertEquals(0, new CloseAllDiffTabs().closeAllDiffTabs());
    }

    private CloseAllDiffTabs toolWith(TopComponent... tcs) {
        return new CloseAllDiffTabs() {
            @Override
            protected Set<TopComponent> getOpenTopComponents() {
                return Set.of(tcs);
            }
        };
    }

    @Test
    public void closeAllDiffTabs_displayNameContainsVs_closesAndCounts() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("A.java vs B.java");

        int count = toolWith(mockTC).closeAllDiffTabs();

        assertEquals(1, count);
        verify(mockTC).close();
    }

    @Test
    public void closeAllDiffTabs_displayNameContainsDiff_closesAndCounts() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("diff viewer");

        int count = toolWith(mockTC).closeAllDiffTabs();

        assertEquals(1, count);
        verify(mockTC).close();
    }

    @Test
    public void closeAllDiffTabs_displayNameContainsDash_closesAndCounts() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("file.java - HEAD");

        int count = toolWith(mockTC).closeAllDiffTabs();

        assertEquals(1, count);
    }

    @Test
    public void closeAllDiffTabs_normalTab_notClosed() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("NormalEditor.java");

        int count = toolWith(mockTC).closeAllDiffTabs();

        assertEquals(0, count);
        verify(mockTC, never()).close();
    }

    @Test
    public void closeAllDiffTabs_closeThrowsException_handledGracefully() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("file vs head");
        doThrow(new RuntimeException("close failed")).when(mockTC).close();

        // Exception is caught internally; count stays 0 (close threw before increment)
        int count = toolWith(mockTC).closeAllDiffTabs();

        assertEquals(0, count);
    }

    @Test
    public void getDescription_returnsNonBlank() {
        assertFalse(new CloseAllDiffTabs().getDescription().isBlank());
    }

    private CloseAllDiffTabs toolWithClassName(TopComponent tc, String className) {
        return new CloseAllDiffTabs() {
            @Override
            protected Set<TopComponent> getOpenTopComponents() { return Set.of(tc); }
            @Override
            protected String getSimpleClassName(TopComponent t) { return className; }
        };
    }

    @Test
    public void closeAllDiffTabs_classNameContainsDiff_closesAndCounts() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("NormalTab"); // no displayName match

        int count = toolWithClassName(mockTC, "DiffViewerComponent").closeAllDiffTabs();

        assertEquals(1, count);
        verify(mockTC).close();
    }

    @Test
    public void closeAllDiffTabs_classNameContainsCompare_closesAndCounts() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("NormalTab"); // no displayName match

        int count = toolWithClassName(mockTC, "CompareEditorTab").closeAllDiffTabs();

        assertEquals(1, count);
        verify(mockTC).close();
    }

    @Test
    public void closeAllDiffTabs_classNameContainsDiff_closeThrows_handledGracefully() {
        TopComponent mockTC = Mockito.mock(TopComponent.class);
        when(mockTC.getDisplayName()).thenReturn("NormalTab");
        doThrow(new RuntimeException("close failed")).when(mockTC).close();

        int count = toolWithClassName(mockTC, "DiffViewerComponent").closeAllDiffTabs();

        assertEquals(0, count);
    }
}
