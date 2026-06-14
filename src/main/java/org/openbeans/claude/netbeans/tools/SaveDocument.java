package org.openbeans.claude.netbeans.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.SaveDocumentParams;
import org.openbeans.claude.netbeans.tools.params.SaveDocumentResult;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

public class SaveDocument implements Tool<SaveDocumentParams, SaveDocumentResult> {

    @Override
    public String getName() {
        return "saveDocument";
    }

    @Override
    public String getDescription() {
        return "Save a document to disk";
    }

    @Override
    public Class<SaveDocumentParams> getParameterClass() {
        return SaveDocumentParams.class;
    }

    protected boolean isPathAllowed(String filePath) {
        return NbUtils.isPathWithinOpenProjects(filePath);
    }

    protected FileObject toFileObject(File file) {
        return FileUtil.toFileObject(file);
    }

    protected DataObject findDataObject(FileObject fo) throws DataObjectNotFoundException {
        return DataObject.find(fo);
    }

    /**
     * Saves a document to disk.
     */
    @Override
    public SaveDocumentResult run(SaveDocumentParams saveParams) throws FileNotFoundException, DataObjectNotFoundException, IOException {
        String filePath = saveParams.getFilePath();

        // Security check: Only allow saving files within open project directories
        if (!isPathAllowed(filePath)) {
            throw new SecurityException("File save denied: Path is not within any open project directory: " + filePath);
        }

        File file = new File(filePath);
        FileObject fileObject = toFileObject(file);

        if (fileObject == null) {
            throw new FileNotFoundException(filePath);
        }
        DataObject dataObject = findDataObject(fileObject);
        if (dataObject == null) {
            // not really possible as find would throw DataObjectNotFoundException
            throw new NullPointerException("dataObject");
        }
        EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);

        if (editorCookie == null) {
            throw new IllegalStateException("File is not editable or not currently managed by an editor");
        }
        // Save the document
        editorCookie.saveDocument();

        SaveDocumentResult result = new SaveDocumentResult();
        result.setFilePath(filePath);
        result.setSaved(true);
        result.setMessage("Document saved successfully");

        return result;
    }

}
