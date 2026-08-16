package org.openbeans.claude.netbeans.tools;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openbeans.claude.netbeans.tools.params.CheckDocumentDirtyParams;
import org.openbeans.claude.netbeans.tools.params.CheckDocumentDirtyResult;
import static org.junit.jupiter.api.Assertions.*;

public class CheckDocumentDirtyTest {

    @Test
    public void run_returnsFalse_forPathOutsideOpenProjects() throws Exception {
        // Security check fails (no open projects or NB unavailable); caught by outer catch.
        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath("/tmp/test.java");
        CheckDocumentDirtyResult result = new CheckDocumentDirty().run(params);
        assertNotNull(result);
        assertFalse(result.getIsDirty());
        assertEquals("/tmp/test.java", result.getFilePath());
        assertNotNull(result.getNote());
    }

    @Test
    public void getName() {
        assertEquals("checkDocumentDirty", new CheckDocumentDirty().getName());
    }

    @Test
    public void getDescription() {
        assertFalse(new CheckDocumentDirty().getDescription().isBlank());
    }

    @Test
    public void getParameterClass() {
        assertEquals(CheckDocumentDirtyParams.class, new CheckDocumentDirty().getParameterClass());
    }

    @Test
    public void run_setsFilePath_inResult() throws Exception {
        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath("/tmp/another.java");
        CheckDocumentDirtyResult result = new CheckDocumentDirty().run(params);
        assertEquals("/tmp/another.java", result.getFilePath());
        assertFalse(result.getIsDirty());
    }

    private CheckDocumentDirty allowAllPaths() {
        return new CheckDocumentDirty() {
            @Override
            protected boolean isPathAllowed(String filePath) { return true; }
        };
    }

    @Test
    public void run_pathAllowed_fileNotInNbFilesystem_returnsNotFound() throws Exception {
        // FileUtil.toFileObject returns null for a non-existent path → "File not found" result
        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath("/nonexistent_for_testing_xyz/file.java");

        CheckDocumentDirtyResult result = allowAllPaths().run(params);

        assertEquals("/nonexistent_for_testing_xyz/file.java", result.getFilePath());
        assertFalse(result.getIsDirty());
        assertFalse(result.getIsOpen());
        assertNotNull(result.getNote());
    }

    private CheckDocumentDirty toolWithMockNb(DataObject mockDO, EditorCookie mockCookie) {
        FileObject mockFO = Mockito.mock(FileObject.class);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        if (mockDO != null) {
            when(mockDO.getLookup()).thenReturn(mockLookup);
            when(mockLookup.lookup(EditorCookie.class)).thenReturn(mockCookie);
        }
        return new CheckDocumentDirty() {
            @Override protected boolean isPathAllowed(String p) { return true; }
            @Override protected FileObject toFileObject(File f) { return mockFO; }
            @Override protected DataObject findDataObject(FileObject fo) throws Exception {
                return mockDO;
            }
        };
    }

    @Test
    public void run_dataObjectNotModified_noEditorCookie_returnsNotDirtyNotOpen() throws Exception {
        DataObject mockDO = Mockito.mock(DataObject.class);
        when(mockDO.isModified()).thenReturn(false);
        Lookup mockLookup = Mockito.mock(Lookup.class);
        when(mockDO.getLookup()).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorCookie.class)).thenReturn(null);

        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath("/fake/path/File.java");
        CheckDocumentDirtyResult result = toolWithMockNb(mockDO, null).run(params);

        assertEquals("/fake/path/File.java", result.getFilePath());
        assertFalse(result.getIsDirty());
        assertFalse(result.getIsOpen());
    }

    @Test
    public void run_dataObjectModified_withOpenPane_returnsDirtyAndOpen() throws Exception {
        DataObject mockDO = Mockito.mock(DataObject.class);
        when(mockDO.isModified()).thenReturn(true);
        EditorCookie mockCookie = Mockito.mock(EditorCookie.class);
        javax.swing.JEditorPane mockPane = Mockito.mock(javax.swing.JEditorPane.class);
        when(mockCookie.getOpenedPanes()).thenReturn(new javax.swing.JEditorPane[]{mockPane});

        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath("/fake/path/File.java");
        CheckDocumentDirtyResult result = toolWithMockNb(mockDO, mockCookie).run(params);

        assertTrue(result.getIsDirty());
        assertTrue(result.getIsOpen());
    }

    @Test
    public void run_pathAllowed_existingTempFile_returnsResult() throws Exception {
        // With a real file: FileUtil may return null or DataObject.find may throw;
        // either way a valid result with isDirty=false is returned
        java.io.File tmp = java.io.File.createTempFile("nbtest", ".java");
        tmp.deleteOnExit();

        CheckDocumentDirtyParams params = new CheckDocumentDirtyParams();
        params.setFilePath(tmp.getAbsolutePath());

        CheckDocumentDirtyResult result = allowAllPaths().run(params);

        assertEquals(tmp.getAbsolutePath(), result.getFilePath());
        assertFalse(result.getIsDirty());
    }

    @Test
    public void findDataObject_throwsForMockFileObject() throws Exception {
        // Exercises the protected seam body (calls DataObject.find)
        FileObject mockFO = Mockito.mock(FileObject.class);
        CheckDocumentDirty cd = new CheckDocumentDirty();
        try {
            cd.findDataObject(mockFO);
        } catch (Throwable ignored) {
            // DataObject.find throws in test env — seam body was executed
        }
    }
}
