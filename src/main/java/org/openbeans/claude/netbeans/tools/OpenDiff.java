package org.openbeans.claude.netbeans.tools;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import org.netbeans.api.diff.Diff;
import org.netbeans.api.diff.DiffView;
import org.netbeans.api.diff.Difference;
import org.netbeans.api.diff.StreamSource;
import org.openbeans.claude.netbeans.EditorUtils;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.Content;
import org.openbeans.claude.netbeans.tools.params.OpenDiffParams;
import org.openbeans.claude.netbeans.tools.params.OpenDiffResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

/**
 * Tool to show a diff of a proposed change and let the user approve or reject it.
 * The response is sent asynchronously once the user approves (FILE_SAVED) or
 * closes the tab (DIFF_REJECTED).
 */
public class OpenDiff implements Tool<OpenDiffParams, AsyncResponse<OpenDiffResult>> {

    private static final Logger LOGGER = Logger.getLogger(OpenDiff.class.getName());

    @Override
    public String getName() {
        return "openDiff";
    }

    @Override
    public String getDescription() {
        return "Open a git diff for the file";
    }

    @Override
    public Class<OpenDiffParams> getParameterClass() {
        return OpenDiffParams.class;
    }

    /** Resolved inputs for a diff. */
    record DiffContents(String oldPath, String newPath, String oldText, String newText, String tabName) {
    }

    /** Signals a problem that should be reported to Claude as the tool result. */
    static final class DiffInputException extends Exception {
        DiffInputException(String message) {
            super(message);
        }
    }

    private static AsyncResponse<OpenDiffResult> immediate(OpenDiffResult result) {
        return handler -> handler.sendResponse(result);
    }

    static OpenDiffResult errorResult(String message) {
        List<Content> contentList = new ArrayList<>();
        contentList.add(new Content("text", message));
        return new OpenDiffResult().withContent(contentList).withIsError(true);
    }

    /** Security check, overridable in tests. */
    boolean isPathAllowed(String path) {
        return NbUtils.isPathWithinOpenProjects(path);
    }

    /** Path of the active editor, overridable in tests. */
    String currentEditorPath() {
        return EditorUtils.getCurrentEditorFilePath();
    }

    /** Buffer content (with unsaved changes) of the active editor, overridable in tests. */
    String currentEditorContent() {
        return EditorUtils.getCurrentEditorContent();
    }

    /**
     * Works out the paths and texts to compare. A missing old file is treated
     * as empty, so diffs for newly created files work.
     */
    DiffContents resolveContents(OpenDiffParams params) throws DiffInputException {
        String oldFilePath = params.getOldFilePath();
        String newFilePath = params.getNewFilePath();

        String editorPath = null;
        String editorContent = null;
        if (oldFilePath == null || newFilePath == null) {
            editorPath = currentEditorPath();
            if (editorPath == null) {
                throw new DiffInputException("No active editor found. Please specify file paths or open a file in the editor.");
            }
            editorContent = currentEditorContent();
        }
        String oldPath = oldFilePath != null ? oldFilePath : editorPath;
        String newPath = newFilePath != null ? newFilePath : editorPath;

        // Security check: Only allow diffing files within open project directories
        if (!isPathAllowed(oldPath)) {
            throw new SecurityException("File access denied: old_file_path is not within any open project directory: " + oldPath);
        }
        if (!isPathAllowed(newPath)) {
            throw new SecurityException("File access denied: new_file_path is not within any open project directory: " + newPath);
        }

        String oldText;
        if (oldFilePath == null && editorContent != null) {
            oldText = editorContent;
        } else if (!new File(oldPath).exists()) {
            // New file: compare against empty content
            oldText = "";
        } else {
            oldText = read(oldPath, "old");
        }

        String newText;
        if (params.getNewFileContents() != null) {
            newText = params.getNewFileContents();
        } else if (newFilePath == null && editorContent != null) {
            newText = editorContent;
        } else if (!new File(newPath).exists()) {
            throw new DiffInputException("New file does not exist: " + newPath);
        } else {
            newText = read(newPath, "new");
        }

        String tabName = params.getTabName() != null ? params.getTabName()
                : "Diff: " + new File(oldPath).getName() + " vs " + new File(newPath).getName();

        return new DiffContents(oldPath, newPath, oldText, newText, tabName);
    }

    private static String read(String path, String which) throws DiffInputException {
        try {
            return Files.readString(new File(path).toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read " + which + " file for diff: " + path, e);
            throw new DiffInputException("Failed to read " + which + " file: " + e.getMessage());
        }
    }

    @Override
    public AsyncResponse<OpenDiffResult> run(OpenDiffParams params) throws Exception {
        final DiffContents contents;
        try {
            contents = resolveContents(params);
        } catch (DiffInputException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Cannot open diff: {0}", e.getMessage());
            return immediate(errorResult(e.getMessage()));
        }

        // The UI is built on the EDT once the handler is known, so the tab is
        // registered before the user can interact with or close it.
        return handler -> SwingUtilities.invokeLater(() -> {
            try {
                showDiff(contents, handler);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error opening diff", e);
                handler.sendResponse(errorResult("Error opening diff: " + e.getMessage()));
            }
        });
    }

    /** Must be called on the EDT. */
    private void showDiff(DiffContents contents, AsyncHandler<OpenDiffResult> handler) throws IOException {
        Diff diffService = Lookup.getDefault().lookup(Diff.class);
        if (diffService == null) {
            LOGGER.warning("Diff service not available - no Diff implementation found in Lookup");
            handler.sendResponse(errorResult("Diff service not available"));
            return;
        }

        DiffView diffView = diffService.createDiff(
                source(new File(contents.oldPath()).getName() + " (original)", contents.oldPath(),
                        mimeTypeFor(contents.oldPath()), contents.oldText()),
                source(new File(contents.newPath()).getName() + " (modified)", contents.newPath(),
                        mimeTypeFor(contents.newPath()), contents.newText()));
        if (diffView == null) {
            LOGGER.warning("Failed to create diff view - diffView is null");
            handler.sendResponse(errorResult("Failed to create diff view"));
            return;
        }

        ClaudeDiffTopComponent diffTC = new ClaudeDiffTopComponent();
        diffTC.setDisplayName(contents.tabName());
        diffTC.setLayout(new BorderLayout());

        JToolBar toolbar = diffView.getToolBar();
        if (toolbar == null) {
            toolbar = new JToolBar();
        }

        JButton approveButton = new JButton("✓ Approve");
        approveButton.setToolTipText("Approve and apply this diff");
        approveButton.addActionListener(e -> {
            LOGGER.info("Diff approved: " + contents.tabName());

            //NOTE: While the message says FILE_SAVED, the IDE does not need to save it. Claude will want to write to it.
            // If needed, we could also do it via
            //
            //    FileObject fo = FileUtil.toFileObject(file);
            //    DataObject dao = DataObject.find(fo);
            //    EditorCookie ec = dao.getLookup().lookup(EditorCookie.class);
            //    StyledDocument doc = ec.openDocument();
            //    doc.remove(0, doc.getLength());
            //    doc.insertString(0, content, null);
            //    SaveCookie save = dao.getLookup().lookup(SaveCookie.class);
            //    save.save();
            // but then Claude actually gets confused:
            //    > Error: File has been unexpectedly modified. Read it again before attempting to write it.
            List<Content> contentList = new ArrayList<>();
            contentList.add(new Content("text", "FILE_SAVED"));
            contentList.add(new Content("text", contents.newText()));

            DiffTabTracker.accept(diffTC, new OpenDiffResult().withContent(contentList));
            // The diff is settled; further clicks must not send another response.
            approveButton.setEnabled(false);

            // Note: We do not close the diff tab here. Claude Code will close the tab via command.
        });

        toolbar.add(approveButton, 0);
        diffTC.add(toolbar, BorderLayout.NORTH);
        diffTC.add(diffView.getComponent(), BorderLayout.CENTER);

        // Register before opening so a close is always seen as a rejection.
        for (Object old : DiffTabTracker.register(diffTC, contents.tabName(), handler)) {
            if (old instanceof TopComponent) {
                ((TopComponent) old).close();
            }
        }

        diffTC.open();
        diffTC.requestActive();
    }

    /**
     * Determines the MIME type used for syntax highlighting in the diff.
     * Files that do not exist yet are resolved by name alone.
     *
     * @param path the file path
     * @return the MIME type, {@code text/plain} if unknown
     */
    static String mimeTypeFor(String path) {
        File file = new File(path);
        String mime = null;
        try {
            FileObject fo = file.exists() ? FileUtil.toFileObject(FileUtil.normalizeFile(file)) : null;
            if (fo == null) {
                fo = FileUtil.createMemoryFileSystem().getRoot().createData(file.getName());
            }
            mime = fo.getMIMEType();
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.FINE, "Cannot determine MIME type of " + path, e);
        }
        return (mime == null || "content/unknown".equals(mime)) ? "text/plain" : mime;
    }

    private static StreamSource source(String name, String title, String mimeType, String text) {
        return new StreamSource() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public String getMIMEType() {
                return mimeType;
            }

            @Override
            public Reader createReader() throws IOException {
                return new StringReader(text);
            }

            @Override
            public Writer createWriter(Difference[] conflicts) throws IOException {
                throw new IOException("Writing not supported for diff content");
            }
        };
    }
}
