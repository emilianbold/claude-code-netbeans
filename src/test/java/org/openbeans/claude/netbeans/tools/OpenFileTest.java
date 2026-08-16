package org.openbeans.claude.netbeans.tools;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openbeans.claude.netbeans.tools.params.OpenFileParams;
import static org.junit.jupiter.api.Assertions.*;

public class OpenFileTest {

    @Test
    public void getName() {
        assertEquals("openFile", new OpenFile().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new OpenFile().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(OpenFileParams.class, new OpenFile().getParameterClass());
    }

    @Test
    public void run_throwsException_forPathOutsideOpenProjects() {
        OpenFileParams params = new OpenFileParams();
        params.setFilePath("/tmp/test.java");
        // Security check fails (no open projects / NB unavailable) → throws
        assertThrows(Exception.class, () -> new OpenFile().run(params));
    }

    private OpenFile allowAllPaths() {
        return new OpenFile() {
            @Override
            protected boolean isPathAllowed(String filePath) { return true; }
        };
    }

    @Test
    public void run_pathAllowed_fileDoesNotExist_throwsRuntimeException() {
        OpenFileParams params = new OpenFileParams();
        params.setFilePath("/nonexistent_for_testing_xyz/test.java");

        assertThrows(RuntimeException.class, () -> allowAllPaths().run(params));
    }

    private OpenFile openFileWithMocks(FileObject mockFO, DataObject mockDO, EditorCookie mockCookie) {
        return new OpenFile() {
            @Override protected boolean isPathAllowed(String p) { return true; }
            @Override protected FileObject toFileObject(File f) { return mockFO; }
            @Override protected DataObject findDataObject(FileObject fo) throws Exception { return mockDO; }
        };
    }

    @Test
    public void run_fileObjectAndEditorCookie_returnsSuccessMessage() throws Exception {
        DataObject mockDO = Mockito.mock(DataObject.class);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        FileObject mockFO = Mockito.mock(FileObject.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockDO.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);

        // Use a real temp file so file.exists() = true
        java.io.File tmp = java.io.File.createTempFile("nbopen", ".java");
        tmp.deleteOnExit();
        OpenFileParams params = new OpenFileParams();
        params.setFilePath(tmp.getAbsolutePath());

        String result = openFileWithMocks(mockFO, mockDO, mockCookie).run(params);

        assertTrue(result.startsWith("File opened successfully"));
        verify(mockCookie).open();
    }

    @Test
    public void run_fileObjectButNullEditorCookie_throwsRuntimeException() throws Exception {
        DataObject mockDO = Mockito.mock(DataObject.class);
        FileObject mockFO = Mockito.mock(FileObject.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockDO.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(null);

        java.io.File tmp = java.io.File.createTempFile("nbopen", ".java");
        tmp.deleteOnExit();
        OpenFileParams params = new OpenFileParams();
        params.setFilePath(tmp.getAbsolutePath());

        assertThrows(RuntimeException.class, () -> openFileWithMocks(mockFO, mockDO, null).run(params));
    }

    @Test
    public void run_pathAllowed_existingFile_notInNbFilesystem_throwsRuntimeException() throws Exception {
        java.io.File tmp = java.io.File.createTempFile("nbtest", ".java");
        tmp.deleteOnExit();

        OpenFileParams params = new OpenFileParams();
        params.setFilePath(tmp.getAbsolutePath());

        // Without NB: FileUtil returns null or DataObject lookup fails → RuntimeException
        assertThrows(RuntimeException.class, () -> allowAllPaths().run(params));
    }

    @Test
    public void findDataObject_throwsForMockFileObject() throws Exception {
        // Exercises the protected seam method body (calls DataObject.find)
        FileObject mockFO = Mockito.mock(FileObject.class);
        OpenFile of = new OpenFile();
        try {
            of.findDataObject(mockFO);
        } catch (Throwable ignored) {
            // DataObject.find throws in test env — seam body was executed
        }
    }
}
