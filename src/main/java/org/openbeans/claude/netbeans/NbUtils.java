package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.annotation.JsonInclude;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.text.NbDocument;
import org.openide.windows.TopComponent;

public class NbUtils {

    /**
     * Data class to hold current text selection information from NetBeans.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelectionData {

        public final String text;
        public final String filePath;
        public final int startLine;
        public final int startColumn;
        public final int endLine;
        public final int endColumn;
        public final boolean isEmpty;

        public SelectionData(String selectedText, String filePath,
                int startLine, int startColumn,
                int endLine, int endColumn) {
            this.text = selectedText != null ? selectedText : "";
            this.filePath = filePath;
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.isEmpty = selectedText == null || selectedText.isEmpty();
        }
    }

    /**
     * Extracts current text selection data from NetBeans IDE.
     *
     * @return SelectionData object with selection information, or null if no
     * selection
     */
    public static SelectionData getCurrentSelectionData() {
        // Get the current active editor
        TopComponent activeTC = TopComponent.getRegistry().getActivated();
        if (activeTC == null) {
            return null;
        }

        Node[] nodes = activeTC.getActivatedNodes();
        if (nodes == null || nodes.length == 0) {
            return null;
        }

        EditorCookie editorCookie = nodes[0].getLookup().lookup(EditorCookie.class);
        if (editorCookie == null) {
            return null;
        }

        // Get the opened editor panes
        JTextComponent[] panes = editorCookie.getOpenedPanes();
        if (panes == null || panes.length == 0) {
            return null;
        }

        // Use the first pane (usually the active one)
        JTextComponent textComponent = panes[0];
        if (textComponent == null || !(textComponent.getDocument() instanceof StyledDocument)) {
            return null;
        }

        StyledDocument doc = (StyledDocument) textComponent.getDocument();
        String selectedText = textComponent.getSelectedText();
        int selectionStart = textComponent.getSelectionStart();
        int selectionEnd = textComponent.getSelectionEnd();

        // Get file path
        String filePath = null;
        DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
        if (dataObject != null) {
            FileObject fileObject = dataObject.getPrimaryFile();
            if (fileObject != null) {
                filePath = fileObject.getPath();
            }
        }

        // Use NbDocument utility methods for line/column calculation
        int startLine = NbDocument.findLineNumber(doc, selectionStart) + 1; // Convert to 1-based
        int startColumn = NbDocument.findLineColumn(doc, selectionStart);
        int endLine = NbDocument.findLineNumber(doc, selectionEnd) + 1; // Convert to 1-based
        int endColumn = NbDocument.findLineColumn(doc, selectionEnd);

        return new SelectionData(selectedText, filePath, startLine, startColumn, endLine, endColumn);
    }

}
