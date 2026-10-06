package org.backblue.config;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.List;
import java.util.Optional;

// Where configuration documents are persisted. Chosen once at startup by Configurator.Source.
public interface ConfigStore {

    /** @return the stored document, or empty if nothing has been stored for it yet. */
    Optional<JSONObject> read(Config file) throws ReadException;

    /** Persists one change. Must be all-or-nothing: on failure, nothing is stored. */
    void save(Change change) throws Exception;

    /** Human-readable location of a document, for log messages. */
    String describe(Config file);

    enum Op { SET, REMOVE, APPEND }

    /**
     * @param tokens   JSON Pointer tokens of the edited path; empty with {@code SET} replaces the whole document
     * @param newValue value written (for {@code APPEND}, the appended element), or null for {@code REMOVE}
     * @param document the complete document after the change
     */
    record Change(Config file, Op op, List<String> tokens, @Nullable Object newValue,
                  JSONObject document, String changedBy) {

        public static Change replace(Config file, JSONObject document, String changedBy) {
            return new Change(file, Op.SET, List.of(), document, document, changedBy);
        }

        public String path() {
            return tokens.isEmpty() ? "" : "/" + String.join("/", tokens.stream()
                    .map(t -> t.replace("~", "~0").replace("/", "~1")).toList());
        }
    }

    class ReadException extends Exception {
        public ReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
