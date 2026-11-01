package org.openbeans.claude.netbeans.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.openbeans.claude.netbeans.tools.params.Editor;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsParams;
import org.openbeans.claude.netbeans.tools.params.GetOpenEditorsResult;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Tool to get list of currently open editor tabs.
 */
public class GetOpenEditors implements Tool<GetOpenEditorsParams, GetOpenEditorsResult> {

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

    /**
     * Returns the node of every open editor window. Other windows (e.g. the
     * Projects view) are skipped even if a file is selected in them.
     */
    List<Node> openEditorNodes() {
        List<Node> nodes = new ArrayList<>();
        WindowManager wm = WindowManager.getDefault();
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (!wm.isOpenedEditorTopComponent(tc)) {
                continue;
            }
            Node[] activated = tc.getActivatedNodes();
            if (activated != null && activated.length > 0) {
                nodes.add(activated[0]);
            }
        }
        return nodes;
    }

    /**
     * Builds the editor list from the given nodes, skipping nodes that are not
     * files and listing each file once (split or cloned editors share a file).
     */
    List<Editor> toEditors(List<Node> nodes) {
        List<Editor> editors = new ArrayList<>();
        Set<FileObject> seen = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getLookup().lookup(EditorCookie.class) == null) {
                continue;
            }
            DataObject dataObject = node.getLookup().lookup(DataObject.class);
            if (dataObject == null) {
                continue;
            }
            FileObject fileObject = dataObject.getPrimaryFile();
            if (fileObject == null || !seen.add(fileObject)) {
                continue;
            }

            Editor editor = new Editor();
            editor.setName(fileObject.getName());
            editor.setPath(fileObject.getPath());
            editor.setExtension(fileObject.getExt());
            editor.setMimeType(fileObject.getMIMEType());

            Project owner = ownerOf(fileObject);
            if (owner != null) {
                editor.setProjectName(ProjectUtils.getInformation(owner).getDisplayName());
                editor.setProjectPath(owner.getProjectDirectory().getPath());
            }
            editors.add(editor);
        }
        return editors;
    }

    /** Project owning the file, or null. Overridable in tests. */
    Project ownerOf(FileObject fileObject) {
        return FileOwnerQuery.getOwner(fileObject);
    }

    @Override
    public GetOpenEditorsResult run(GetOpenEditorsParams params) throws Exception {
        GetOpenEditorsResult result = new GetOpenEditorsResult();
        List<Editor> editors = new ArrayList<>();
        try {
            editors = toEditors(openEditorNodes());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting open documents", e);
        }
        result.setEditors(editors);
        return result;
    }
}
