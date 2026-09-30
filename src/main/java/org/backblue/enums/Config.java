package org.backblue.enums;

import org.backblue.utilities.Resources;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public enum Config {

    Settings_JSON("settings.json", true),
    Rulebook_JSON("rulebook.json", true),
    Badges_JSON("badges.json", true),
    Deployment_Audit_JSON("deployment-audit.json", false),
    Deployment_Triggers_JSON("deployment-triggers.json", false),
    Features_JSON("features.json", true),
    Bot_Properties("bot.properties", true);

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

    // Reads from data/<fileName>, if exists, and dynamically updates it; if not, copies the default into the directory.
    // Will only return nil/throwable IFF the file does not exist and cannot be dynamically read
    public static @Nullable JSONObject loadResource(Config file) {
        String path = "data/" + file.fileName();
        boolean required = file.required();

        JSONObject resource;
        boolean updated = false;
        JSONObject defaultResource = readDefault(file);

        try {
            resource = new JSONObject(Files.readString(Path.of(path)));
        } catch (NoSuchFileException e) {
            try {
                resource = readDefault(file);
                updated = true;
            } catch (Exception ex) {
                if (required) throw new RuntimeException("Required source cannot be loaded " + path, ex);
                Log.error("Very bad execution attempting to load default resource {}", path, ex);
                return null;
            }
        } catch (SecurityException e) {
            Log.error("Permission denied to read resource. Delete {} to have it regenerated", path);
            return required ? defaultResource : null;
        } catch (Exception e) {
            Log.error("Cannot read local file. Delete {} to have it regenerated", path);
            return required ? defaultResource : null;
        }

        if (!updated) {
            for (String key : defaultResource.keySet()) {
                if (key.equals("_version")) continue;
                if (!resource.has(key)) {
                    updated = true;
                    resource.put(key, defaultResource.get(key));
                } else {
                    boolean isSameType = isSameDataType(resource.get(key), defaultResource.get(key));
                    if (!isSameType) {
                        Log.error("Cannot parse correctness. Delete {} to have it regenerated", path);
                        return required ? defaultResource : null;
                    }
                }
            }
        }

        int currVersion = resource.optInt("_version", Integer.MIN_VALUE);
        if (currVersion != Integer.MIN_VALUE) {
            if (defaultResource.optInt("_version", Integer.MIN_VALUE) > currVersion) {
                Log.warn("Unable to write to {}. Read configuration is newer than current packaged", path);
                return resource;
            } if (defaultResource.optInt("_version", Integer.MIN_VALUE) < currVersion) {
                resource.put("_version", defaultResource.getInt("_version"));
                updated = true;
            }
        }

        if (updated) {
            try (FileWriter fw = new FileWriter(path)) {
                fw.write(resource.toString(4));
                Log.info("A resource was updated: {}", path);
            } catch (IOException e) {
                Log.warn("Unable to write to {}. New features/improvements may not be enabled. Check if file is accessible & write-able.", path);
            }
        }

        return resource;
    }

    private static JSONObject readDefault(Config file) throws JSONException, Config.Error {
        String path = "defaults/" + file.fileName();
        try {
            return new JSONObject(Resources.readString(path));
        } catch (IOException e) {
            throw new Config.Error("Cannot load internal files " + path);
        }
    }

    private static boolean isSameDataType(Object a, Object b) {
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
