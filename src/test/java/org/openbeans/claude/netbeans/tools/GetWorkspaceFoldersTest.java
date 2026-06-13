package org.openbeans.claude.netbeans.tools;

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
}
