package org.backblue.config;

import org.backblue.utilities.Util;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public enum Config {

    Settings_JSON("settings.json", true),
    Rulebook_JSON("rulebook.json", true),
    Badges_JSON("badges.json", true),
    Deployment_Audit_JSON("deployment-audit.json", false),
    Deployment_Triggers_JSON("deployment-triggers.json", false),
    Features_JSON("features.json", true);

    private static final Logger Log = LoggerFactory.getLogger(Config.class);

    private final String fileName;
    private final boolean required;

    Config(String fileName, boolean required) {
        this.fileName = fileName;
        this.required = required;
    }

    public String fileName() {
        return fileName;
    }
    public boolean required() {
        return required;
    }
    // Storage key, e.g. "features" for features.json.
    public String key() {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    // Reads the document from the store and merges in keys added to the packaged defaults since it was saved.
    // If the store has nothing yet, it is seeded from importFrom (when given and present), otherwise from the defaults.
    // Returns null only for optional documents that cannot be read.
    public static @Nullable JSONObject loadResource(Config file, ConfigStore store, @Nullable ConfigStore importFrom) {
        String location = store.describe(file);
        boolean required = file.required();

        JSONObject defaultResource = readDefault(file);
        JSONObject resource;
        boolean updated = false;
        boolean fromDefaults = false;
        String changedBy = "system";

        try {
            Optional<JSONObject> stored = store.read(file);
            if (stored.isPresent()) {
                resource = stored.get();
            } else {
                updated = true;
                Optional<JSONObject> imported = Optional.empty();
                if (importFrom != null) {
                    try {
                        imported = importFrom.read(file);
                    } catch (ConfigStore.ReadException e) {
                        Log.warn("Not importing {}: {}", importFrom.describe(file), e.getMessage());
                    }
                }
                if (imported.isPresent()) {
                    resource = imported.get();
                    changedBy = "import";
                    Log.info("Importing {} into {}", importFrom.describe(file), location);
                } else {
                    resource = new JSONObject(defaultResource.toString());
                    fromDefaults = true;
                }
            }
        } catch (ConfigStore.ReadException e) {
            Log.error(e.getMessage(), e.getCause());
            return required ? defaultResource : null;
        }

        if (!fromDefaults) {
            for (String key : defaultResource.keySet()) {
                if (key.equals("_version")) continue;
                if (!resource.has(key)) {
                    updated = true;
                    resource.put(key, defaultResource.get(key));
                } else {
                    boolean isSameType = isSameDataType(resource.get(key), defaultResource.get(key));
                    if (!isSameType) {
                        Log.error("Cannot parse correctness. Delete {} to have it regenerated", location);
                        return required ? defaultResource : null;
                    }
                }
            }
        }

        int currVersion = resource.optInt("_version", Integer.MIN_VALUE);
        if (currVersion != Integer.MIN_VALUE) {
            if (defaultResource.optInt("_version", Integer.MIN_VALUE) > currVersion) {
                Log.warn("Unable to write to {}. Read configuration is newer than current packaged", location);
                return resource;
            } if (defaultResource.optInt("_version", Integer.MIN_VALUE) < currVersion) {
                resource.put("_version", defaultResource.getInt("_version"));
                updated = true;
            }
        }

        if (updated) {
            try {
                store.save(ConfigStore.Change.replace(file, resource, changedBy));
                Log.info("A resource was updated: {}", location);
            } catch (Exception e) {
                Log.warn("Unable to write to {}. New features/improvements may not be enabled. Check if it is accessible & write-able.", location, e);
            }
        }

        return resource;
    }

    public static JSONObject readDefault(Config file) throws JSONException, Config.Error {
        String path = "defaults/" + file.fileName();
        try {
            return new JSONObject(Util.readResource(path));
        } catch (IOException e) {
            throw new Config.Error("Cannot load internal files " + path);
        }
    }

    public static boolean isSameDataType(Object a, Object b) {
        if (a == JSONObject.NULL || b == JSONObject.NULL) return a == b;
        if (a instanceof JSONArray && b instanceof JSONArray) return true;
        if (a instanceof JSONObject && b instanceof JSONObject) return true;
        if (a instanceof Number && b instanceof Number) return true;
        if (a instanceof String && b instanceof String) return true;
        return a instanceof Boolean && b instanceof Boolean;
    }

    public static class Error extends RuntimeException {
        public Error(String message) {
            super(message);
        }
    }

}
