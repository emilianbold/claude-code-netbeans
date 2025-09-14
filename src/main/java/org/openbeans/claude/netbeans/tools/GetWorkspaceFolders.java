package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.tools.params.GetWorkspaceFoldersParams;
import org.openbeans.claude.netbeans.tools.params.GetWorkspaceFoldersResult;
import org.openbeans.claude.netbeans.tools.params.Folder;

/**
 * Tool to get list of workspace folders (open projects).
 */
public class GetWorkspaceFolders implements Tool<GetWorkspaceFoldersParams> {
    
    private static final Logger LOGGER = Logger.getLogger(GetWorkspaceFolders.class.getName());
    
    @Override
    public String getName() {
        return "getWorkspaceFolders";
    }
    
    @Override
    public String getDescription() {
        return "Get list of workspace folders (open projects)";
    }
    
    @Override
    public Class<GetWorkspaceFoldersParams> getParameterClass() {
        return GetWorkspaceFoldersParams.class;
    }
    
    /**
     * Data class to hold project information.
     */
    private static class ProjectData {
        final String path;
        final String displayName;
        
        ProjectData(String path, String displayName) {
            this.path = path;
            this.displayName = displayName;
        }
    }
    
    /**
     * Retrieves project data from NetBeans Platform.
     */
    private List<ProjectData> getOpenProjectsData() {
        List<ProjectData> projectDataList = new ArrayList<>();
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        
        for (Project project : openProjects) {
            String path = project.getProjectDirectory().getPath();
            String displayName = ProjectUtils.getInformation(project).getDisplayName();
            projectDataList.add(new ProjectData(path, displayName));
        }
        
        return projectDataList;
    }
    
    @Override
    public JsonNode run(GetWorkspaceFoldersParams params, MCPResponseBuilder responseBuilder) throws Exception {
        GetWorkspaceFoldersResult result = new GetWorkspaceFoldersResult();
        List<Folder> folders = new ArrayList<>();

        // Get project data using existing method
        List<ProjectData> projectDataList = getOpenProjectsData();

        // Build MCP response from the data
        for (ProjectData projectData : projectDataList) {
            Folder folder = new Folder();
            folder.setName(projectData.displayName);
            folder.setUri("file://" + projectData.path);
            folders.add(folder);
        }

        result.setFolders(folders);
        return responseBuilder.createToolResponse(result);
    }
}