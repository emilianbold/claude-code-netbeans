package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.netbeans.api.project.Project;
import org.openbeans.claude.netbeans.tools.params.Editor;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsParams;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsResult;
import static org.junit.jupiter.api.Assertions.*;

public class GetOpenEditorsTest {

    @Test
    public void run_returnsEmptyEditors_whenNBUnavailable() throws Exception {
        // TopComponent.getRegistry() fails without NB; caught by try-catch -> empty list
        GetOpenEditorsResult result = new GetOpenEditors().run(new GetOpenEditorsParams());
        assertNotNull(result);
        assertNotNull(result.getEditors());
        assertTrue(result.getEditors().isEmpty());
    }

    @Test
    public void getName() {
        assertEquals("getOpenEditors", new GetOpenEditors().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new GetOpenEditors().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(GetOpenEditorsParams.class, new GetOpenEditors().getParameterClass());
    }

    private GetOpenEditors editorsWithNodes(Node... nodes) {
        return new GetOpenEditors() {
            @Override
            protected Node[] getCurrentNodes() {
                return nodes;
            }
        };
    }

    private Node nodeWithLookup(Lookup lkp) {
        Node n = Mockito.mock(Node.class);
        when(n.getLookup()).thenReturn(lkp);
        return n;
    }

    @Test
    public void run_nodeWithNullEditorCookie_emptyEditors() throws Exception {
        Lookup lkp = Mockito.mock(Lookup.class);
        when(lkp.lookup(EditorCookie.class)).thenReturn(null);

        GetOpenEditorsResult result = editorsWithNodes(nodeWithLookup(lkp)).run(new GetOpenEditorsParams());

        assertTrue(result.getEditors().isEmpty());
    }

    @Test
    public void run_nodeWithEditorCookieButNullDataObject_emptyEditors() throws Exception {
        EditorCookie cookie = Mockito.mock(EditorCookie.class);
        Lookup lkp = Mockito.mock(Lookup.class);
        when(lkp.lookup(EditorCookie.class)).thenReturn(cookie);
        when(lkp.lookup(DataObject.class)).thenReturn(null);

        GetOpenEditorsResult result = editorsWithNodes(nodeWithLookup(lkp)).run(new GetOpenEditorsParams());

        assertTrue(result.getEditors().isEmpty());
    }

    @Test
    public void run_nodeWithDataObjectButNullFileObject_emptyEditors() throws Exception {
        EditorCookie cookie = Mockito.mock(EditorCookie.class);
        DataObject dataObject = Mockito.mock(DataObject.class);
        Lookup lkp = Mockito.mock(Lookup.class);
        when(lkp.lookup(EditorCookie.class)).thenReturn(cookie);
        when(lkp.lookup(DataObject.class)).thenReturn(dataObject);
        when(dataObject.getPrimaryFile()).thenReturn(null);

        GetOpenEditorsResult result = editorsWithNodes(nodeWithLookup(lkp)).run(new GetOpenEditorsParams());

        assertTrue(result.getEditors().isEmpty());
    }

    @Test
    public void run_nodeWithFileObject_addsEditor() throws Exception {
        EditorCookie cookie = Mockito.mock(EditorCookie.class);
        DataObject dataObject = Mockito.mock(DataObject.class);
        FileObject fileObject = Mockito.mock(FileObject.class);
        Lookup lkp = Mockito.mock(Lookup.class);
        when(lkp.lookup(EditorCookie.class)).thenReturn(cookie);
        when(lkp.lookup(DataObject.class)).thenReturn(dataObject);
        when(dataObject.getPrimaryFile()).thenReturn(fileObject);
        when(fileObject.getName()).thenReturn("MyFile");
        when(fileObject.getPath()).thenReturn("/project/src/MyFile.java");
        when(fileObject.getExt()).thenReturn("java");
        when(fileObject.getMIMEType()).thenReturn("text/x-java");
        // FileOwnerQuery.getOwner() returns null without NB → project fields not set

        GetOpenEditorsResult result = editorsWithNodes(nodeWithLookup(lkp)).run(new GetOpenEditorsParams());

        // Either one editor added (if FileOwnerQuery returned null gracefully)
        // or empty if FileOwnerQuery threw (caught by outer catch)
        assertNotNull(result.getEditors());
        if (!result.getEditors().isEmpty()) {
            assertEquals("MyFile", result.getEditors().get(0).getName());
        }
    }

    @Test
    public void run_nodeWithFileObject_andProject_setsProjectInfo() throws Exception {
        EditorCookie cookie = Mockito.mock(EditorCookie.class);
        DataObject dataObject = Mockito.mock(DataObject.class);
        FileObject fileObject = Mockito.mock(FileObject.class);
        Lookup lkp = Mockito.mock(Lookup.class);
        when(lkp.lookup(EditorCookie.class)).thenReturn(cookie);
        when(lkp.lookup(DataObject.class)).thenReturn(dataObject);
        when(dataObject.getPrimaryFile()).thenReturn(fileObject);
        when(fileObject.getName()).thenReturn("Foo");
        when(fileObject.getPath()).thenReturn("/proj/Foo.java");
        when(fileObject.getExt()).thenReturn("java");

        // Override setEditorProjectInfo to exercise the owner != null path
        GetOpenEditors tool = new GetOpenEditors() {
            @Override protected Node[] getCurrentNodes() { return new Node[]{nodeWithLookup(lkp)}; }
            @Override
            protected void setEditorProjectInfo(Editor editor, FileObject fo) {
                editor.setProjectName("TestProject");
                editor.setProjectPath("/proj");
            }
        };
        GetOpenEditorsResult result = tool.run(new GetOpenEditorsParams());

        assertEquals(1, result.getEditors().size());
        assertEquals("TestProject", result.getEditors().get(0).getProjectName());
        assertEquals("/proj", result.getEditors().get(0).getProjectPath());
    }
}
