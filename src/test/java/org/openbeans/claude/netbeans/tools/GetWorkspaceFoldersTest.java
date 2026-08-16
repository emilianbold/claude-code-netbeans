package org.openbeans.claude.netbeans.tools;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.tools.params.GetWorkspaceFoldersParams;
import org.openbeans.claude.netbeans.tools.params.GetWorkspaceFoldersResult;
import static org.junit.jupiter.api.Assertions.*;

public class GetWorkspaceFoldersTest {

    @Test
    public void getName() {
        assertEquals("getWorkspaceFolders", new GetWorkspaceFolders().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new GetWorkspaceFolders().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(GetWorkspaceFoldersParams.class, new GetWorkspaceFolders().getParameterClass());
    }

    @Test
    public void run_returnsEmptyFolders_whenNoProjectsOpen() throws Exception {
        // OpenProjects.getDefault().getOpenProjects() returns empty array in test env
        GetWorkspaceFoldersResult result = new GetWorkspaceFolders().run(new GetWorkspaceFoldersParams());
        assertNotNull(result);
        assertNotNull(result.getFolders());
        assertTrue(result.getFolders().isEmpty());
    }

    @Test
    public void run_withFakeProjects_returnsFoldersList() throws Exception {
        GetWorkspaceFolders tool = new GetWorkspaceFolders() {
            @Override
            List<GetWorkspaceFolders.ProjectData> getOpenProjectsData() {
                return List.of(
                    new GetWorkspaceFolders.ProjectData("/path/to/project", "TestProject"),
                    new GetWorkspaceFolders.ProjectData("/other/project", "OtherProject")
                );
            }
        };
        GetWorkspaceFoldersResult result = tool.run(new GetWorkspaceFoldersParams());

        assertNotNull(result);
        assertEquals(2, result.getFolders().size());
        assertEquals("TestProject", result.getFolders().get(0).getName());
        assertEquals("file:///path/to/project", result.getFolders().get(0).getUri());
        assertEquals("OtherProject", result.getFolders().get(1).getName());
        assertEquals("file:///other/project", result.getFolders().get(1).getUri());
    }

    @Test
    public void run_withSingleFakeProject_returnsSingleFolder() throws Exception {
        GetWorkspaceFolders tool = new GetWorkspaceFolders() {
            @Override
            List<GetWorkspaceFolders.ProjectData> getOpenProjectsData() {
                return List.of(new GetWorkspaceFolders.ProjectData("/src/myproject", "MyProject"));
            }
        };
        GetWorkspaceFoldersResult result = tool.run(new GetWorkspaceFoldersParams());

        assertEquals(1, result.getFolders().size());
        assertEquals("MyProject", result.getFolders().get(0).getName());
        assertTrue(result.getFolders().get(0).getUri().endsWith("myproject"));
    }
}
