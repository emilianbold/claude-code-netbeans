package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.text.NbDocument;
import org.openide.windows.TopComponent;

public class NbUtils {
    private static final Logger LOGGER = Logger.getLogger(NbUtils.class.getName());

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
 
    /**
     * Security validation: Checks if the given file path is within any open project directory.
     * This prevents unauthorized file operations outside of the user's active workspace.
     * 
     * @param filePath the file path to validate
     * @return true if the path is within an open project, false otherwise
     */
    public static boolean isPathWithinOpenProjects(String filePath) {
        try {
            File targetFile = new File(filePath).getCanonicalFile();
            String targetPath = targetFile.getAbsolutePath();
            
            // Get all open projects
            Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
            
            for (Project project : openProjects) {
                FileObject projectDir = project.getProjectDirectory();
                if (projectDir != null) {
                    File projectFile = new File(projectDir.getPath()).getCanonicalFile();
                    String projectPath = projectFile.getAbsolutePath();
                    
                    // Check if target path is within this project directory
                    if (targetPath.startsWith(projectPath + File.separator) || targetPath.equals(projectPath)) {
                        LOGGER.log(Level.FINE, "File path {0} is within project: {1}", 
                                  new Object[]{filePath, projectPath});
                        return true;
                    }
                }
            }
            
            LOGGER.log(Level.WARNING, "File path {0} is not within any open project directory", filePath);
            return false;
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error validating file path: " + filePath, e);
            return false; // Deny access on any path resolution errors
        }
    }
}
