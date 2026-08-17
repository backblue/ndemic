package org.backblue.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

class Configurator {

    static final Logger Log = LoggerFactory.getLogger(Configurator.class);
    Bot bot;

    @NotNull final Properties properties;
    @NotNull final JSONObject settingsFile;
    @NotNull final JSONObject badgesFile;
    @NotNull final JSONObject rulebookFile;
    @NotNull final JSONObject featuresFile;
    final JSONObject deploymentAuditFile;
    final JSONObject deploymentAutoresponderFile;

    public Configurator(Bot bot) {
        this.bot = bot;
        try {
            properties = new Properties();
            properties.load(new StringReader(Files.readString(Path.of("data/bot.properties"))));
        } catch (IOException | IllegalArgumentException e ) {
            throw new Configurator.Error("Required files in data directory could not be loaded or parsed: bot.properties");
        }

        this.settingsFile = Objects.requireNonNull(reloadableResource("data/settings.json", true));
        this.badgesFile = Objects.requireNonNull(reloadableResource("data/badges.json", true));
        this.rulebookFile = Objects.requireNonNull(reloadableResource("data/rulebook.json", true));
        this.featuresFile = Objects.requireNonNull(reloadableResource("data/features.json", true));
        this.deploymentAuditFile = reloadableResource("data/deployment-audit.json", false);
        this.deploymentAutoresponderFile = reloadableResource("data/deployment-triggers.json", false);
    }

    // Reads from path, if exists, and dynamically updates settings; if not, copies file into directory.
    // Will only return nil/throwable IFF the path does not exist and cannot be dynamically read
    private @Nullable JSONObject reloadableResource(String path, boolean required) throws RuntimeException {
        JSONObject resource;
        boolean updated = false;
        JSONObject defaultResource = this.readReloadableDefault(path);

        try {
            resource = new JSONObject(Files.readString(Path.of(path)));
        } catch (NoSuchFileException e) {
            try {
                resource = this.readReloadableDefault(path);
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
                if (!resource.has(key)) {
                    updated = true;
                    resource.put(key, defaultResource.get(key));
                } else {
                    boolean isSameType = this.isSameDataType(resource.get(key), defaultResource.get(key));
                    if (!isSameType) {
                        Log.error("Cannot parse correctness. Delete {} to have it regenerated", path);
                        return required ? defaultResource : null;
                    }
                }
            }
        }

        if (updated) {
            try (FileWriter fw = new FileWriter(path)) {
                fw.write(resource.toString());
                Log.info("A resource was created: {}", path);
            } catch (IOException e) {
                Log.warn("Unable to update {}. New features/improvements may not be enabled. Check if file is accessible & write-able.", path);
            }
        }

        return resource;
    }

    private JSONObject readReloadableDefault(String path) throws JSONException, Configurator.Error {
        path = path.replace("data/", "defaults/");
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new FileNotFoundException(path);
            return new JSONObject(new String(stream.readAllBytes()));
        } catch (IOException e) {
            throw new Configurator.Error("Cannot load internal files " + path);
        }
    }

    private boolean isSameDataType(Object a, Object b) {
        if (a == JSONObject.NULL || b == JSONObject.NULL) return a == b;
        if (a instanceof JSONArray && b instanceof JSONArray) return true;
        if (a instanceof JSONObject && b instanceof JSONObject) return true;
        if (a instanceof Number && b instanceof Number) return true;
        if (a instanceof String && b instanceof String) return true;
        return a instanceof Boolean && b instanceof Boolean;
    }

    static class Error extends RuntimeException {
        public Error(String message) {
            super(message);
        }
    }
}