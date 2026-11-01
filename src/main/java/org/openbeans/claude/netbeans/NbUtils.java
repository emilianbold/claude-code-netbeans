package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.text.NbDocument;
import org.openide.windows.TopComponent;

public class NbUtils {
    private static final Logger LOGGER = Logger.getLogger(NbUtils.class.getName());

    /** A zero-based position in a document, as in LSP. */
    public static class Position {
        public final int line;
        public final int character;

        public Position(int line, int character) {
            this.line = line;
            this.character = character;
        }
    }

    /** A selection range with zero-based positions. */
    public static class Selection {
        public final Position start;
        public final Position end;
        public final boolean isEmpty;

        public Selection(Position start, Position end, boolean isEmpty) {
            this.start = start;
            this.end = end;
            this.isEmpty = isEmpty;
        }
    }

    /**
     * The current text selection, in the same shape as Claude Code's other IDE
     * integrations use for both getCurrentSelection and selection_changed:
     * { success, text, filePath, fileUrl, selection: { start, end, isEmpty } }.
     * Lines and characters are zero-based.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelectionData {

        public final boolean success;
        public final String text;
        public final String filePath;
        public final String fileUrl;
        public final Selection selection;
        public final String message;

        private SelectionData(boolean success, String text, String filePath, String fileUrl,
                Selection selection, String message) {
            this.success = success;
            this.text = text;
            this.filePath = filePath;
            this.fileUrl = fileUrl;
            this.selection = selection;
            this.message = message;
        }

        /**
         * @param message why no selection is available
         * @return a result signalling that there is no selection
         */
        public static SelectionData none(String message) {
            return new SelectionData(false, null, null, null, null, message);
        }

        /**
         * Reads the selection of a text component.
         *
         * @param textComponent the editor pane
         * @param doc its document
         * @param filePath absolute path of the file shown, may be null
         * @return the selection data
         */
        public static SelectionData of(JTextComponent textComponent, StyledDocument doc, String filePath) {
            String selectedText = textComponent.getSelectedText();
            int startOffset = textComponent.getSelectionStart();
            int endOffset = textComponent.getSelectionEnd();
            boolean empty = selectedText == null || selectedText.isEmpty();
            Selection selection = new Selection(position(doc, startOffset), position(doc, endOffset), empty);
            return new SelectionData(true, empty ? "" : selectedText, filePath,
                    filePath != null ? toFileUri(filePath) : null, selection, null);
        }
    }

    /**
     * @param doc a document
     * @param offset an offset in it
     * @return the zero-based line and character of the offset
     */
    public static Position position(StyledDocument doc, int offset) {
        return new Position(NbDocument.findLineNumber(doc, offset), NbDocument.findLineColumn(doc, offset));
    }

    /**
     * Extracts current text selection data from NetBeans IDE.
     *
     * @return SelectionData object with selection information, or null if no
     * editor is active
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

        return SelectionData.of(textComponent, (StyledDocument) textComponent.getDocument(), filePathOf(nodes[0]));
    }

    /**
     * @param node a node
     * @return absolute path of the local file the node represents, or null
     */
    public static String filePathOf(Node node) {
        DataObject dataObject = node.getLookup().lookup(DataObject.class);
        if (dataObject == null || dataObject.getPrimaryFile() == null) {
            return null;
        }
        File file = FileUtil.toFile(dataObject.getPrimaryFile());
        return file != null ? file.getAbsolutePath() : null;
    }
 
    /**
     * Converts a local path to a properly encoded file URI, e.g.
     * {@code /a b/C.java} becomes {@code file:///a%20b/C.java}. Directory
     * URIs are returned without a trailing slash.
     *
     * @param path absolute file system path
     * @return the file URI
     */
    public static String toFileUri(String path) {
        String uri = Paths.get(path).toAbsolutePath().toUri().toString();
        if (uri.endsWith("/") && !uri.equals("file:///")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    /**
     * Converts a file URI (or a plain path) to a local path. Percent-encoded
     * characters are decoded. Unencoded URIs such as {@code file:///a b/c}
     * are accepted as well.
     *
     * @param uriOrPath a {@code file:} URI or a path
     * @return the local file system path
     */
    public static String toFilePath(String uriOrPath) {
        if (!uriOrPath.startsWith("file:")) {
            return uriOrPath;
        }
        try {
            return Paths.get(URI.create(uriOrPath)).toString();
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            // Not a valid URI (e.g. unencoded spaces): strip the scheme
            String rest = uriOrPath.substring("file:".length());
            if (rest.startsWith("//")) {
                rest = rest.substring(2);
            }
            return rest;
        }
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
