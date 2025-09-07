package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsParams;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

/**
 * Tool to get list of currently open editor tabs.
 */
public class GetOpenEditors implements Tool<GetOpenEditorsParams> {
    
    private static final Logger LOGGER = Logger.getLogger(GetOpenEditors.class.getName());
    
    @Override
    public String getName() {
        return "getOpenEditors";
    }
    
    @Override
    public String getDescription() {
        return "Get list of currently open editor tabs";
    }
    
    @Override
    public Class<GetOpenEditorsParams> getParameterClass() {
        return GetOpenEditorsParams.class;
    }
    
    @Override
    public JsonNode run(GetOpenEditorsParams params, MCPResponseBuilder responseBuilder) throws Exception {
        ArrayNode documents = responseBuilder.arrayNode();
        
        try {
            // Use TopComponent registry to get open editor nodes
            Node[] nodes = TopComponent.getRegistry().getCurrentNodes();
            for (Node node : nodes) {
                EditorCookie editorCookie = node.getLookup().lookup(EditorCookie.class);
                if (editorCookie != null) {
                    DataObject dataObject = node.getLookup().lookup(DataObject.class);
                    if (dataObject != null) {
                        FileObject fileObject = dataObject.getPrimaryFile();
                        if (fileObject != null) {
                            ObjectNode docInfo = responseBuilder.objectNode();
                            docInfo.put("name", fileObject.getName());
                            docInfo.put("path", fileObject.getPath());
                            docInfo.put("extension", fileObject.getExt());
                            docInfo.put("mimeType", fileObject.getMIMEType());
                            
                            // Get the project owner using FileOwnerQuery
                            Project owner = FileOwnerQuery.getOwner(fileObject);
                            if (owner != null) {
                                docInfo.put("projectName", ProjectUtils.getInformation(owner).getDisplayName());
                                docInfo.put("projectPath", owner.getProjectDirectory().getPath());
                            }
                            
                            documents.add(docInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting open documents", e);
        }
        
        return responseBuilder.createToolResponse(documents);
    }
}