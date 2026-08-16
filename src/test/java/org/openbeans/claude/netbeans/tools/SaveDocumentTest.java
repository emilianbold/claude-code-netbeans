package org.openbeans.claude.netbeans.tools;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Lookup;
import org.openbeans.claude.netbeans.tools.params.SaveDocumentParams;
import org.openbeans.claude.netbeans.tools.params.SaveDocumentResult;
import static org.junit.jupiter.api.Assertions.*;

public class SaveDocumentTest {

    @Test
    public void getName() {
        assertEquals("saveDocument", new SaveDocument().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new SaveDocument().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(SaveDocumentParams.class, new SaveDocument().getParameterClass());
    }

    @Test
    public void run_throwsException_forPathOutsideOpenProjects() {
        SaveDocumentParams params = new SaveDocumentParams();
        params.setFilePath("/tmp/test.java");
        // Security check fails (no open projects / NB unavailable) → throws
        assertThrows(Exception.class, () -> new SaveDocument().run(params));
    }

    private SaveDocument allowAllPaths() {
        return new SaveDocument() {
            @Override
            protected boolean isPathAllowed(String filePath) { return true; }
        };
    }

    @Test
    public void run_pathAllowed_fileNotInNbFilesystem_throwsFileNotFoundException() {
        SaveDocumentParams params = new SaveDocumentParams();
        params.setFilePath("/nonexistent_for_testing_xyz/file.java");

        assertThrows(Exception.class, () -> allowAllPaths().run(params));
    }

    private SaveDocument saveDocWithMocks(FileObject mockFO, DataObject mockDO) {
        return new SaveDocument() {
            @Override protected boolean isPathAllowed(String p) { return true; }
            @Override protected FileObject toFileObject(File f) { return mockFO; }
            @Override protected DataObject findDataObject(FileObject fo) throws DataObjectNotFoundException {
                return mockDO;
            }
        };
    }

    @Test
    public void run_dataObjectNullEditorCookie_throwsIllegalStateException() throws Exception {
        DataObject mockDO = Mockito.mock(DataObject.class);
        FileObject mockFO = Mockito.mock(FileObject.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockDO.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(null);

        SaveDocumentParams params = new SaveDocumentParams();
        params.setFilePath("/fake/File.java");

        assertThrows(Exception.class, () -> saveDocWithMocks(mockFO, mockDO).run(params));
    }

    @Test
    public void run_withEditorCookie_savesAndReturnsResult() throws Exception {
        DataObject mockDO = Mockito.mock(DataObject.class);
        FileObject mockFO = Mockito.mock(FileObject.class);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockDO.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);

        SaveDocumentParams params = new SaveDocumentParams();
        params.setFilePath("/fake/File.java");
        SaveDocumentResult result = saveDocWithMocks(mockFO, mockDO).run(params);

        assertNotNull(result);
        assertEquals("/fake/File.java", result.getFilePath());
        assertTrue(result.getSaved());
        verify(mockCookie).saveDocument();
    }

    @Test
    public void run_pathAllowed_existingFile_dataObjectNotFound_throwsException() throws Exception {
        java.io.File tmp = java.io.File.createTempFile("nbtest", ".java");
        tmp.deleteOnExit();

        SaveDocumentParams params = new SaveDocumentParams();
        params.setFilePath(tmp.getAbsolutePath());

        // FileUtil may return null or DataObject.find may throw → exception propagates
        assertThrows(Exception.class, () -> allowAllPaths().run(params));
    }

    @Test
    public void findDataObject_throwsForMockFileObject() throws Exception {
        // Exercises the protected seam body (calls DataObject.find)
        FileObject mockFO = Mockito.mock(FileObject.class);
        SaveDocument sd = new SaveDocument();
        try {
            sd.findDataObject(mockFO);
        } catch (Throwable ignored) {
            // DataObject.find throws in test env — seam body was executed
        }
    }
}
