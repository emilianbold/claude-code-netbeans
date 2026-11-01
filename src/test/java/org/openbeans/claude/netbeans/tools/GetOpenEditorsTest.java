package org.openbeans.claude.netbeans.tools;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.Editor;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.Lookups;
import static org.junit.jupiter.api.Assertions.*;

public class GetOpenEditorsTest {

    private static final EditorCookie EDITOR = (EditorCookie) java.lang.reflect.Proxy.newProxyInstance(
            EditorCookie.class.getClassLoader(), new Class<?>[]{EditorCookie.class}, (p, m, a) -> null);

    private static Node editorNode(FileObject file) throws Exception {
        return new AbstractNode(Children.LEAF, Lookups.fixed(EDITOR, DataObject.find(file)));
    }

    @Test
    public void listsEveryOpenEditorOnceAndSkipsNonFiles() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject a = root.createData("A.java");
        FileObject b = root.createData("B.txt");

        Node notAFile = new AbstractNode(Children.LEAF);
        List<Node> nodes = List.of(editorNode(a), notAFile, editorNode(b), editorNode(a));

        GetOpenEditors tool = new GetOpenEditors() {
            @Override
            List<Node> openEditorNodes() {
                return nodes;
            }

            @Override
            org.netbeans.api.project.Project ownerOf(FileObject fileObject) {
                return null;
            }
        };

        List<Editor> editors = tool.run(null).getEditors();

        assertEquals(2, editors.size());
        assertEquals("A", editors.get(0).getName());
        assertEquals("java", editors.get(0).getExtension());
        assertEquals("B", editors.get(1).getName());
    }
}
