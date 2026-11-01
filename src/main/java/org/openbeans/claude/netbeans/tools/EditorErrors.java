package org.openbeans.claude.netbeans.tools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.HintsController;
import org.openide.filesystems.FileObject;

/**
 * Reads the errors, warnings and hints NetBeans shows for a file, with their
 * exact ranges.
 * <p>
 * The editor gutter only records which line a problem is on. The exact
 * ranges live in {@link ErrorDescription}s held by the hints module's
 * {@code AnnotationHolder}, which is not public API. It is reached through the
 * hints module's own class loader; if that ever fails (e.g. the class changes
 * in a future NetBeans release), callers get {@code null} and fall back to
 * line-based ranges.
 */
final class EditorErrors {

    private static final Logger LOGGER = Logger.getLogger(EditorErrors.class.getName());
    private static final String HOLDER_CLASS = "org.netbeans.modules.editor.hints.AnnotationHolder";

    private static volatile boolean unavailable;

    private EditorErrors() {
    }

    /**
     * Returns the problems shown for a file that is open in an editor, or
     * {@code null} if they cannot be read.
     */
    static List<ErrorDescription> forFile(FileObject file) {
        if (unavailable || file == null) {
            return null;
        }
        try {
            Class<?> holderClass = Class.forName(HOLDER_CLASS, true, HintsController.class.getClassLoader());
            Method getInstance = holderClass.getMethod("getInstance", FileObject.class);
            Object holder = getInstance.invoke(null, file);
            if (holder == null) {
                return null;
            }
            List<?> errors = (List<?>) holderClass.getMethod("getErrors").invoke(holder);
            List<ErrorDescription> result = new ArrayList<>();
            for (Object error : errors) {
                if (error instanceof ErrorDescription) {
                    result.add((ErrorDescription) error);
                }
            }
            return result;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            unavailable = true;
            LOGGER.log(Level.INFO, "Exact diagnostic ranges unavailable; using whole lines", e);
            return null;
        }
    }
}
