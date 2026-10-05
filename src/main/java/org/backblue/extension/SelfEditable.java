package org.backblue.extension;

import org.backblue.config.ConfigService;
import org.backblue.config.Config;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

/**
 * Implementing classes own one section of a configuration document and are the only ones responsible for editing it.
 * Paths are JSON Pointers (e.g. {@code /channels/debugEnforcement}) and must fall inside {@link #scope()}.
 */
public interface SelfEditable {

    Scope scope();

    /** @return {@code false} if the owned document was not loaded; edits would throw. */
    default boolean editable() {
        return ConfigService.isLoaded(scope().document());
    }

    default @Nullable Object get(String path) {
        return ConfigService.get(checked(path), path);
    }

    /** @return {@code true} if the change was persisted. */
    default boolean set(String path, Object value, String changedBy) {
        return ConfigService.set(checked(path), path, value, changedBy);
    }

    /** @return {@code true} if the change was persisted. */
    default boolean remove(String path, String changedBy) {
        return ConfigService.remove(checked(path), path, changedBy);
    }

    /** @return {@code true} if the change was persisted. */
    default boolean append(String arrayPath, Object value, String changedBy) {
        return ConfigService.append(checked(arrayPath), arrayPath, value, changedBy);
    }

    /** Replaces the entire owned section. @return {@code true} if the change was persisted. */
    default boolean replace(JSONObject section, String changedBy) {
        Scope scope = scope();
        return ConfigService.set(scope.document(), scope.root(), section, changedBy);
    }

    private Config checked(String path) {
        Scope scope = scope();
        if (!scope.contains(path)) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " cannot edit '" + path
                    + "'; it only owns '" + scope.root() + "' in " + scope.document().fileName());
        }
        return scope.document();
    }

    /**
     * @param document the configuration document owned
     * @param root JSON Pointer to the owned section; {@code ""} owns the whole document
     */
    record Scope(Config document, String root) {

        public Scope {
            if (!root.isEmpty() && !root.startsWith("/")) {
                throw new IllegalArgumentException("Scope root must be \"\" or start with '/': " + root);
            }
        }

        public static Scope of(Config document, String root) {
            return new Scope(document, root);
        }

        public static Scope whole(Config document) {
            return new Scope(document, "");
        }

        public boolean contains(String path) {
            if (root.isEmpty()) return path.isEmpty() || path.startsWith("/");
            return path.equals(root) || path.startsWith(root + "/");
        }
    }
}
