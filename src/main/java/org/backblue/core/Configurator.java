package org.backblue.core;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

class Configurator {

    Bot bot;

    @NotNull final Properties properties;
    @NotNull final JSONObject settingsFile;
    @NotNull final JSONObject badgesFile;
    @NotNull final JSONObject rulebookFile;
    @NotNull final JSONObject featuresFile;
    @NotNull JSONObject deploymentAuditFile;
    @NotNull JSONObject deploymentAutoresponderFile;

    public Configurator(Bot bot) {
        this.bot = bot;
        try {
            properties = new Properties();
            properties.load(new StringReader(Files.readString(Path.of("data/bot.properties"))));
            settingsFile = new JSONObject(Files.readString(Path.of("data/settings.json")));
            rulebookFile = new JSONObject(Files.readString(Path.of("data/rulebook.json")));
            badgesFile = new JSONObject(Files.readString(Path.of("data/badges.json")));
            featuresFile = new JSONObject(Files.readString(Path.of("data/features.json")));
        } catch (JSONException e) {
            throw new Configurator.Error("Required files or values in data directory could not be parsed: bot.properties, settings.json, rulebook.json, and badges.json");
        } catch (IOException e) {
            throw new Configurator.Error("Required files in data directory could not be loaded: bot.properties, settings.json, rulebook.json, and badges.json");
        }
    }

    // Reads from path, if exists, and dynamically updates settings; if not, copies file into directory.
    private JSONObject reloadableResource(String path) {
        return null;
    }

    private JSONObject readReloadableDefault(String path) throws JSONException, Configurator.Error {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new FileNotFoundException(path);
            return new JSONObject(new String(stream.readAllBytes()));
        } catch (IOException e) {
            throw new Configurator.Error("Cannot load internal files " + path);
        }
    }

    static class Error extends RuntimeException {
        public Error(String message) {
            super(message);
        }
    }
}