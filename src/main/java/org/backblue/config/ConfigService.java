package org.backblue.config;

import org.backblue.config.ConfigStore.Change;
import org.backblue.config.ConfigStore.Op;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Single write path for configuration documents edited at runtime (see SelfEditable).
// Each edit is validated and applied to a copy, persisted through the ConfigStore, and only then applied
// in place to the loaded JSONObject, so a failed save changes nothing and existing holders see successful edits.
public final class ConfigService {

    private static final Logger Log = LoggerFactory.getLogger(ConfigService.class);

    private static final Map<Config, JSONObject> documents = new EnumMap<>(Config.class);
    private static final Map<Config, JSONObject> defaults = new EnumMap<>(Config.class);
    private static ConfigStore store;

    private ConfigService() {
        throw new UnsupportedOperationException("ConfigService cannot be instantiated");
    }

    static synchronized void init(ConfigStore configStore) {
        store = configStore;
    }

    static synchronized void register(Config file, JSONObject document) {
        documents.put(file, document);
        defaults.put(file, Config.readDefault(file));
    }

    public static synchronized boolean isLoaded(Config file) {
        return documents.containsKey(file);
    }

    public static @Nullable Object get(Config file, String path) {
        JSONObject document = document(file);
        synchronized (document) {
            return copy(resolve(document, tokens(path)));
        }
    }

    public static boolean set(Config file, String path, Object value, String changedBy) {
        List<String> tokens = tokens(path);
        if (!tokens.isEmpty() && tokens.getLast().equals("-")) {
            return append(file, path.substring(0, path.length() - 2), value, changedBy);
        }
        Object wrapped = JSONObject.wrap(value);
        checkType(file, tokens, wrapped);
        if (tokens.isEmpty() && !(wrapped instanceof JSONObject)) {
            throw new IllegalArgumentException("Whole document replacement requires a JSONObject");
        }
        return edit(file, Op.SET, tokens, wrapped, changedBy);
    }

    public static boolean remove(Config file, String path, String changedBy) {
        List<String> tokens = tokens(path);
        if (tokens.isEmpty()) throw new IllegalArgumentException("Cannot remove the whole document");
        checkNotVersion(tokens);
        return edit(file, Op.REMOVE, tokens, null, changedBy);
    }

    public static boolean append(Config file, String arrayPath, Object value, String changedBy) {
        return edit(file, Op.APPEND, tokens(arrayPath), JSONObject.wrap(value), changedBy);
    }

    private static boolean edit(Config file, Op op, List<String> tokens, @Nullable Object value, String changedBy) {
        JSONObject document = document(file);
        ConfigStore configStore;
        synchronized (ConfigService.class) {
            configStore = store;
        }
        if (configStore == null) throw new IllegalStateException("ConfigService has no store");

        synchronized (document) {
            JSONObject updated = new JSONObject(document.toString());
            if (!apply(updated, op, tokens, copy(value))) return true;

            String where = display(tokens) + " in " + configStore.describe(file);
            try {
                configStore.save(new Change(file, op, tokens, value, updated, changedBy));
            } catch (Exception e) {
                Log.warn("Unable to save {} {}. Nothing was changed.", op, where, e);
                return false;
            }
            apply(document, op, tokens, copy(value));
            Log.info("{} {} {}", changedBy, op, where);
            return true;
        }
    }

    // Returns false if the edit is a no-op (removing an absent key). Throws if the path is invalid.
    private static boolean apply(JSONObject document, Op op, List<String> tokens, @Nullable Object value) {
        if (op == Op.APPEND) {
            if (!(resolve(document, tokens) instanceof JSONArray arr)) {
                throw new IllegalArgumentException("No array at '" + display(tokens) + "'");
            }
            arr.put(value);
            return true;
        }
        if (tokens.isEmpty()) {
            if (!(value instanceof JSONObject replacement)) {
                throw new IllegalArgumentException("Whole document replacement requires a JSONObject");
            }
            replaceContents(document, replacement);
            return true;
        }

        Object parent = resolve(document, tokens.subList(0, tokens.size() - 1));
        String last = tokens.getLast();
        if (parent instanceof JSONObject obj) {
            if (op == Op.REMOVE) {
                if (!obj.has(last)) return false;
                obj.remove(last);
            } else {
                obj.put(last, value);
            }
        } else if (parent instanceof JSONArray arr) {
            int i = index(last, arr, tokens);
            if (op == Op.REMOVE) arr.remove(i);
            else arr.put(i, value);
        } else {
            throw new IllegalArgumentException("Parent of '" + display(tokens) + "' does not exist");
        }
        return true;
    }

    // Adds new keys before dropping old ones, so unlocked readers never see an empty document. Keeps _version.
    private static void replaceContents(JSONObject document, JSONObject replacement) {
        for (String key : replacement.keySet()) {
            if (!key.equals("_version")) document.put(key, replacement.get(key));
        }
        for (String key : new ArrayList<>(document.keySet())) {
            if (!key.equals("_version") && !replacement.has(key)) document.remove(key);
        }
    }

    private static JSONObject document(Config file) {
        JSONObject document;
        synchronized (ConfigService.class) {
            document = documents.get(file);
        }
        if (document == null) throw new IllegalStateException(file.fileName() + " is not loaded");
        return document;
    }

    private static void checkType(Config file, List<String> tokens, Object value) {
        checkNotVersion(tokens);
        JSONObject fileDefaults;
        synchronized (ConfigService.class) {
            fileDefaults = defaults.get(file);
        }
        Object expected = resolve(fileDefaults, tokens);
        if (expected != null && !tokens.isEmpty() && !Config.isSameDataType(value, expected)) {
            throw new IllegalArgumentException("Type mismatch at '" + display(tokens) + "' in " + file.fileName());
        }
    }

    private static void checkNotVersion(List<String> tokens) {
        if (tokens.size() == 1 && tokens.getFirst().equals("_version")) {
            throw new IllegalArgumentException("_version is managed by Config and cannot be edited");
        }
    }

    private static @Nullable Object copy(@Nullable Object value) {
        if (value instanceof JSONObject obj) return new JSONObject(obj.toString());
        if (value instanceof JSONArray arr) return new JSONArray(arr.toString());
        return value;
    }

    // JSON Pointer (RFC 6901) handling

    private static List<String> tokens(String path) {
        if (path.isEmpty()) return List.of();
        if (!path.startsWith("/")) throw new IllegalArgumentException("Path must be \"\" or start with '/': " + path);
        List<String> tokens = new ArrayList<>();
        for (String raw : path.substring(1).split("/", -1)) {
            tokens.add(raw.replace("~1", "/").replace("~0", "~"));
        }
        return tokens;
    }

    private static @Nullable Object resolve(@Nullable Object node, List<String> tokens) {
        for (String token : tokens) {
            if (node instanceof JSONObject obj) {
                node = obj.opt(token);
            } else if (node instanceof JSONArray arr) {
                try {
                    node = arr.opt(Integer.parseInt(token));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return node;
    }

    private static int index(String token, JSONArray arr, List<String> tokens) {
        try {
            int i = Integer.parseInt(token);
            if (i >= 0 && i < arr.length()) return i;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Invalid array index in '" + display(tokens) + "'");
    }

    private static String display(List<String> tokens) {
        return tokens.isEmpty() ? "/" : "/" + String.join("/", tokens);
    }
}
