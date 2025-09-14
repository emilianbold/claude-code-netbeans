package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsParams;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsResult;
import org.openbeans.claude.netbeans.tools.params.Editor;
import java.util.ArrayList;
import java.util.List;
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
        GetOpenEditorsResult result = new GetOpenEditorsResult();
        List<Editor> editors = new ArrayList<>();

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
                            Editor editor = new Editor();
                            editor.setName(fileObject.getName());
                            editor.setPath(fileObject.getPath());
                            editor.setExtension(fileObject.getExt());
                            editor.setMimeType(fileObject.getMIMEType());

                            // Get the project owner using FileOwnerQuery
                            Project owner = FileOwnerQuery.getOwner(fileObject);
                            if (owner != null) {
                                editor.setProjectName(ProjectUtils.getInformation(owner).getDisplayName());
                                editor.setProjectPath(owner.getProjectDirectory().getPath());
                            }

                            editors.add(editor);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting open documents", e);
        }

        result.setEditors(editors);
        return responseBuilder.createToolResponse(result);
    }
}