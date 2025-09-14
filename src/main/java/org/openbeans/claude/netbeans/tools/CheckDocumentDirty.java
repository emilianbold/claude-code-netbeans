package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.CheckDocumentDirtyParams;
import org.openbeans.claude.netbeans.tools.params.CheckDocumentDirtyResult;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * Tool to check if a document has unsaved changes.
 */
public class CheckDocumentDirty implements Tool<CheckDocumentDirtyParams> {
    
    private static final Logger LOGGER = Logger.getLogger(CheckDocumentDirty.class.getName());
    
    @Override
    public String getName() {
        return "checkDocumentDirty";
    }
    
    @Override
    public String getDescription() {
        return "Check if a document has unsaved changes";
    }
    
    @Override
    public Class<CheckDocumentDirtyParams> getParameterClass() {
        return CheckDocumentDirtyParams.class;
    }
    
    @Override
    public JsonNode run(CheckDocumentDirtyParams params, MCPResponseBuilder responseBuilder) throws Exception {
        String filePath = params.getFilePath();
        
        try {
            // Security check: Only allow checking files within open project directories
            if (!NbUtils.isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File access denied: Path is not within any open project directory: " + filePath);
            }
            
            File file = new File(filePath);
            FileObject fileObject = FileUtil.toFileObject(file);
            
            if (fileObject != null) {
                try {
                    DataObject dataObject = DataObject.find(fileObject);
                    if (dataObject != null) {
                        boolean isDirty = dataObject.isModified();

                        // Also check if the file is currently open in an editor
                        EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                        boolean isOpen = editorCookie != null && editorCookie.getOpenedPanes() != null && editorCookie.getOpenedPanes().length > 0;

                        CheckDocumentDirtyResult result = new CheckDocumentDirtyResult();
                        result.setFilePath(filePath);
                        result.setIsDirty(isDirty);
                        result.setIsOpen(isOpen);

                        return responseBuilder.createToolResponse(result);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error checking DataObject for file: " + filePath, e);
                }
            }
            
            // File not found or not a NetBeans-managed file
            CheckDocumentDirtyResult result = new CheckDocumentDirtyResult();
            result.setFilePath(filePath);
            result.setIsDirty(false);
            result.setIsOpen(false);
            result.setNote("File not found or not currently managed by NetBeans");

            return responseBuilder.createToolResponse(result);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking document dirty state: " + filePath, e);
            return responseBuilder.createToolResponse("Error checking document dirty state: " + e.getMessage());
        }
    }
}