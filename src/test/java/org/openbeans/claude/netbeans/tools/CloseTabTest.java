package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.openbeans.claude.netbeans.tools.params.CloseTabParams;
import java.util.List;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;

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

    private static TopComponent tab(String displayName, FileObject file) throws Exception {
        TopComponent tc = new TopComponent();
        tc.setDisplayName(displayName);
        if (file != null) {
            tc.setActivatedNodes(new Node[]{
                new AbstractNode(Children.LEAF, Lookups.fixed(DataObject.find(file)))});
        }
        return tc;
    }

    private static ClaudeDiffTopComponent diffTab(String displayName) {
        ClaudeDiffTopComponent tc = new ClaudeDiffTopComponent();
        tc.setDisplayName(displayName);
        return tc;
    }

    @Test
    public void prefersClaudeDiffTabWithExactName() throws Exception {
        TopComponent editor = tab("Fix bug", null);
        ClaudeDiffTopComponent diff = diffTab("Fix bug");
        assertSame(diff, CloseTab.findTopComponent("Fix bug", List.of(editor, diff)));
    }

    @Test
    public void matchesUniqueFileName() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        TopComponent a = tab("A.java [modified]", root.createData("A.java"));
        TopComponent b = tab("B.java", root.createData("B.java"));
        assertSame(a, CloseTab.findTopComponent("A.java", List.of(a, b)));
        assertSame(a, CloseTab.findTopComponent("A", List.of(a, b)));
    }

    @Test
    public void ambiguousFileNameClosesNothing() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        TopComponent first = tab("Util.java", root.createFolder("p1").createData("Util.java"));
        TopComponent second = tab("Util.java", root.createFolder("p2").createData("Util.java"));
        assertNull(CloseTab.findTopComponent("Util.java", List.of(first, second)));
    }

    @Test
    public void unknownNameMatchesNothing() throws Exception {
        assertNull(CloseTab.findTopComponent("nope", List.of(diffTab("other"))));
    }
}
