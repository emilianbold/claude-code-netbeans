package org.openbeans.claude.netbeans.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openbeans.claude.netbeans.tools.params.OpenDiffParams;
import org.openbeans.claude.netbeans.tools.params.OpenDiffResult;
import static org.junit.jupiter.api.Assertions.*;

public class OpenDiffTest {

    @TempDir
    Path project;

    /** OpenDiff with all paths inside the temp dir allowed and no active editor. */
    private OpenDiff tool() {
        return new OpenDiff() {
            @Override
            boolean isPathAllowed(String path) {
                return path.startsWith(project.toString());
            }

            @Override
            String currentEditorPath() {
                return null;
            }
        };
    }

    private static OpenDiffParams params(String oldPath, String newPath, String newContents, String tabName) {
        OpenDiffParams p = new OpenDiffParams();
        p.setOldFilePath(oldPath);
        p.setNewFilePath(newPath);
        p.setNewFileContents(newContents);
        p.setTabName(tabName);
        return p;
    }

    @Test
    public void newFileIsComparedAgainstEmptyContent() throws Exception {
        String path = project.resolve("New.java").toString();

        OpenDiff.DiffContents c = tool().resolveContents(params(path, path, "class New {}", "tab"));

        assertEquals("", c.oldText());
        assertEquals("class New {}", c.newText());
        assertEquals("tab", c.tabName());
    }

    @Test
    public void existingFileIsReadFromDisk() throws Exception {
        Path file = project.resolve("A.java");
        Files.writeString(file, "old");

        OpenDiff.DiffContents c = tool().resolveContents(params(file.toString(), file.toString(), "new", "tab"));

        assertEquals("old", c.oldText());
        assertEquals("new", c.newText());
    }

    @Test
    public void missingNewFileWithoutContentsIsAnError() {
        String path = project.resolve("Missing.java").toString();

        assertThrows(OpenDiff.DiffInputException.class,
                () -> tool().resolveContents(params(path, path, null, "tab")));
    }

    @Test
    public void pathsOutsideProjectsAreDenied() {
        assertThrows(SecurityException.class,
                () -> tool().resolveContents(params("/etc/passwd", "/etc/passwd", "x", "tab")));
    }

    @Test
    public void runReportsInputErrorsImmediately() throws Exception {
        OpenDiffResult[] result = new OpenDiffResult[1];
        tool().run(params("/etc/passwd", "/etc/passwd", "x", "tab")).setHandler(r -> result[0] = r);

        String status = result[0].getContent().get(0).getText();
        assertTrue(status.startsWith("File access denied"), status);
        assertTrue(result[0].getIsError());
    }
}
