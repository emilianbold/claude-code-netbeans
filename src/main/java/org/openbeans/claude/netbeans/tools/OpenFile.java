package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.OpenFileParams;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * Tool to open a file in the NetBeans editor.
 */
public class OpenFile implements Tool<OpenFileParams> {
    
    private static final Logger LOGGER = Logger.getLogger(OpenFile.class.getName());
    
    @Override
    public String getName() {
        return "openFile";
    }
    
    @Override
    public String getDescription() {
        return "Opens a file in the editor";
    }
    
    @Override
    public Class<OpenFileParams> getParameterClass() {
        return OpenFileParams.class;
    }
    
    @Override
    public JsonNode run(OpenFileParams params, MCPResponseBuilder responseBuilder) throws Exception {
        String filePath = params.getFilePath();
        boolean preview = params.getPreview() != null ? params.getPreview() : false;
        
        try {
            // Security check: Only allow opening files within open project directories
            if (!NbUtils.isPathWithinOpenProjects(filePath)) {
                throw new SecurityException("File open denied: Path is not within any open project directory: " + filePath);
            }
            
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IllegalArgumentException("File does not exist: " + filePath);
            }
            
            FileObject fileObject = FileUtil.toFileObject(file);
            if (fileObject != null) {
                DataObject dataObject = DataObject.find(fileObject);
                EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                
                if (editorCookie != null) {
                    // Open the file in NetBeans editor
                    editorCookie.open();
                    
                    return responseBuilder.createToolResponse("File opened successfully: " + filePath);
                }
            }
            
            throw new RuntimeException("Failed to open file in editor");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to open file: " + e.getMessage(), e);
        }
    }
}