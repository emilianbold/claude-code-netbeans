package org.openbeans.claude.netbeans.tools;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.diff.Diff;
import org.netbeans.api.diff.DiffView;
import org.netbeans.api.diff.Difference;
import org.netbeans.api.diff.StreamSource;
import org.openbeans.claude.netbeans.EditorUtils;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.OpenDiffParams;
import org.openbeans.claude.netbeans.tools.params.OpenDiffResult;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

/**
 * Tool to open a git diff for the file.
 */
public class OpenDiff implements Tool<OpenDiffParams, OpenDiffResult> {
    
    private static final Logger LOGGER = Logger.getLogger(OpenDiff.class.getName());
    
    @Override
    public String getName() {
        return "openDiff";
    }
    
    @Override
    public String getDescription() {
        return "Open a git diff for the file";
    }
    
    @Override
    public Class<OpenDiffParams> getParameterClass() {
        return OpenDiffParams.class;
    }

    @Override
    public OpenDiffResult run(OpenDiffParams params) throws Exception {
        String oldFilePath = params.getOldFilePath();
        String newFilePath = params.getNewFilePath();
        String newFileContents = params.getNewFileContents();
        String tabName = params.getTabName();
        
        try {
            // Track if we're using the current editor for defaults
            boolean usingCurrentEditor = false;
            String currentEditorPath = null;
            String currentEditorContent = null;
            
            // If paths are not provided, use current active editor
            String finalOldFilePath;
            String finalNewFilePath;
            if (oldFilePath == null || newFilePath == null) {
                currentEditorPath = EditorUtils.getCurrentEditorFilePath();
                if (currentEditorPath == null) {
                    OpenDiffResult errorResult = new OpenDiffResult();
                    errorResult.setSuccess(false);
                    errorResult.setMessage("No active editor found. Please specify file paths or open a file in the editor.");
                    return errorResult;
                }
                usingCurrentEditor = true;
                currentEditorContent = EditorUtils.getCurrentEditorContent(); // Get the buffer content with unsaved changes
                finalOldFilePath = (oldFilePath == null) ? currentEditorPath : oldFilePath;
                finalNewFilePath = (newFilePath == null) ? currentEditorPath : newFilePath;
            } else {
                finalOldFilePath = oldFilePath;
                finalNewFilePath = newFilePath;
            }
            
            // Security check: Only allow diffing files within open project directories
            if (!NbUtils.isPathWithinOpenProjects(finalOldFilePath)) {
                throw new SecurityException("File access denied: old_file_path is not within any open project directory: " + finalOldFilePath);
            }
            if (!NbUtils.isPathWithinOpenProjects(finalNewFilePath)) {
                throw new SecurityException("File access denied: new_file_path is not within any open project directory: " + finalNewFilePath);
            }
            
            // Read the old file content
            final File oldFile = new File(finalOldFilePath);
            final String oldFileContents;
            
            // If old file is the current editor and we're using it as default, use the editor buffer content
            if (usingCurrentEditor && oldFilePath == null && finalOldFilePath.equals(currentEditorPath) && currentEditorContent != null) {
                oldFileContents = currentEditorContent;
            } else {
                // Otherwise read from disk
                if (!oldFile.exists()) {
                    LOGGER.warning("Old file does not exist for diff: " + finalOldFilePath);
                    OpenDiffResult errorResult = new OpenDiffResult();
                    errorResult.setSuccess(false);
                    errorResult.setMessage("Old file does not exist: " + finalOldFilePath);
                    return errorResult;
                }
                try {
                    oldFileContents = Files.readString(oldFile.toPath(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to read old file for diff: " + finalOldFilePath, e);
                    OpenDiffResult errorResult = new OpenDiffResult();
                    errorResult.setSuccess(false);
                    errorResult.setMessage("Failed to read old file: " + e.getMessage());
                    return errorResult;
                }
            }
            
            // If new file contents not provided, determine how to get them
            final String finalNewFileContents;
            if (newFileContents == null) {
                // If new file is the current editor and we're using it as default, use the editor buffer content
                if (usingCurrentEditor && newFilePath == null && finalNewFilePath.equals(currentEditorPath) && currentEditorContent != null) {
                    finalNewFileContents = currentEditorContent;
                } else {
                    // Otherwise read from disk
                    File newFile = new File(finalNewFilePath);
                    if (!newFile.exists()) {
                        LOGGER.warning("New file does not exist for diff: " + finalNewFilePath);
                        OpenDiffResult errorResult = new OpenDiffResult();
                        errorResult.setSuccess(false);
                        errorResult.setMessage("New file does not exist: " + finalNewFilePath);
                        return errorResult;
                    }
                    try {
                        finalNewFileContents = Files.readString(newFile.toPath(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to read new file for diff: " + finalNewFilePath, e);
                        OpenDiffResult errorResult = new OpenDiffResult();
                        errorResult.setSuccess(false);
                        errorResult.setMessage("Failed to read new file: " + e.getMessage());
                        return errorResult;
                    }
                }
            } else {
                finalNewFileContents = newFileContents;
            }
            
            // Create stream sources for diff
            StreamSource oldSource = new StreamSource() {
                @Override
                public String getName() {
                    return oldFile.getName() + " (original)";
                }
                
                @Override
                public String getTitle() {
                    return finalOldFilePath;
                }
                
                @Override
                public String getMIMEType() {
                    return "text/plain";
                }
                
                @Override
                public Reader createReader() throws IOException {
                    return new StringReader(oldFileContents);
                }
                
                @Override
                public Writer createWriter(Difference[] conflicts) throws IOException {
                    throw new IOException("Writing not supported for original file");
                }
            };
            
            StreamSource newSource = new StreamSource() {
                @Override
                public String getName() {
                    return new File(finalNewFilePath).getName() + " (modified)";
                }
                
                @Override
                public String getTitle() {
                    return finalNewFilePath;
                }
                
                @Override
                public String getMIMEType() {
                    return "text/plain";
                }
                
                @Override
                public Reader createReader() throws IOException {
                    return new StringReader(finalNewFileContents);
                }
                
                @Override
                public Writer createWriter(Difference[] conflicts) throws IOException {
                    throw new IOException("Writing not supported for modified content");
                }
            };
            
            // Get Diff service and create diff view
            Diff diffService = Lookup.getDefault().lookup(Diff.class);
            if (diffService != null) {
                try {
                    String diffTabName = tabName != null ? tabName : 
                        "Diff: " + oldFile.getName() + " vs " + new File(finalNewFilePath).getName();
                        
                    DiffView diffView = diffService.createDiff(oldSource, newSource);
                    if (diffView != null) {
                        // Open the diff view
                        java.awt.Component component = diffView.getComponent();
                        if (component instanceof TopComponent) {
                            TopComponent diffTC = (TopComponent) component;
                            diffTC.setDisplayName(diffTabName);
                            diffTC.open();
                            diffTC.requestActive();
                        } else {
                            // If it's not a TopComponent, try to show it in another way
                            LOGGER.log(Level.WARNING, "Diff component is not a TopComponent: {0}", component.getClass());
                        }
                        
                        OpenDiffResult result = new OpenDiffResult();
                        result.setSuccess(true);
                        result.setTabName(diffTabName);
                        result.setOldFile(finalOldFilePath);
                        result.setNewFile(finalNewFilePath);
                        result.setMessage("Diff viewer opened successfully");

                        return result;
                    } else {
                        LOGGER.warning("Failed to create diff view - diffView is null");
                        OpenDiffResult errorResult = new OpenDiffResult();
                        errorResult.setSuccess(false);
                        errorResult.setMessage("Failed to create diff view");
                        return errorResult;
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error creating diff view", e);
                    OpenDiffResult errorResult = new OpenDiffResult();
                    errorResult.setSuccess(false);
                    errorResult.setMessage("Error creating diff: " + e.getMessage());
                    return errorResult;
                }
            } else {
                LOGGER.warning("Diff service not available - no Diff implementation found in Lookup");
                OpenDiffResult errorResult = new OpenDiffResult();
                errorResult.setSuccess(false);
                errorResult.setMessage("Diff service not available");
                return errorResult;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error opening diff", e);
            OpenDiffResult errorResult = new OpenDiffResult();
            errorResult.setSuccess(false);
            errorResult.setMessage("Error opening diff: " + e.getMessage());
            return errorResult;
        }
    }
}