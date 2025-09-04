package org.openbeans.claude.netbeans.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.openbeans.claude.netbeans.MCPResponseBuilder;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.SaveDocumentParams;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

public class SaveDocument implements Tool<SaveDocumentParams> {

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

    /**
     * Saves a document to disk.
     */
    @Override
    public JsonNode run(SaveDocumentParams saveParams, MCPResponseBuilder responseBuilder) throws FileNotFoundException, DataObjectNotFoundException, IOException {
        String filePath = saveParams.getFilePath();

        // Security check: Only allow saving files within open project directories
        if (!NbUtils.isPathWithinOpenProjects(filePath)) {
            throw new SecurityException("File save denied: Path is not within any open project directory: " + filePath);
        }

        File file = new File(filePath);
        FileObject fileObject = FileUtil.toFileObject(file);

        if (fileObject == null) {
            throw new FileNotFoundException(filePath);
        }
        DataObject dataObject = DataObject.find(fileObject);
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

        ObjectNode result = responseBuilder.objectNode();
        result.put("filePath", filePath);
        result.put("saved", true);
        result.put("message", "Document saved successfully");

        return responseBuilder.createToolResponse(result);
    }

}
