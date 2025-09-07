package org.openbeans.claude.netbeans;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

/**
 * Utility methods for working with NetBeans editors.
 */
public class EditorUtils {
    
    private static final Logger LOGGER = Logger.getLogger(EditorUtils.class.getName());
    
    /**
     * Gets the file path of the currently active editor.
     * 
     * @return the absolute path of the file in the active editor, or null if no editor is active
     */
    public static String getCurrentEditorFilePath() {
        try {
            TopComponent activated = TopComponent.getRegistry().getActivated();
            if (activated != null) {
                Node[] nodes = activated.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    DataObject dataObject = nodes[0].getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        FileObject fileObject = dataObject.getPrimaryFile();
                        if (fileObject != null) {
                            File file = FileUtil.toFile(fileObject);
                            if (file != null) {
                                return file.getAbsolutePath();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current editor file path", e);
        }
        return null;
    }
    
    /**
     * Gets the content of the currently active editor from its document buffer.
     * This includes any unsaved changes.
     * 
     * @return the content of the active editor, or null if no editor is active
     */
    public static String getCurrentEditorContent() {
        try {
            TopComponent activated = TopComponent.getRegistry().getActivated();
            if (activated != null) {
                Node[] nodes = activated.getActivatedNodes();
                if (nodes != null && nodes.length > 0) {
                    EditorCookie editorCookie = nodes[0].getLookup().lookup(EditorCookie.class);
                    if (editorCookie != null) {
                        Document doc = editorCookie.getDocument();
                        if (doc != null) {
                            return doc.getText(0, doc.getLength());
                        } else {
                            // Document not yet loaded, open it first
                            doc = editorCookie.openDocument();
                            if (doc != null) {
                                return doc.getText(0, doc.getLength());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current editor content", e);
        }
        return null;
    }
}