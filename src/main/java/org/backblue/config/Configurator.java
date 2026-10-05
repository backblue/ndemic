package org.backblue.config;

import org.backblue.core.Bot;
import org.backblue.utilities.Database;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

public final class Configurator {

    private static final Logger Log = LoggerFactory.getLogger(Configurator.class);

    Bot bot;

    @NotNull final Properties properties;
    private final Map<Config, JSONObject> documents = new EnumMap<>(Config.class);
    @NotNull final Source source;
    @NotNull final ConfigStore store;

    // Where configuration documents are stored, chosen by CONFIG_SOURCE in bot.properties.
    // FILE needs no database, for local development.
    public enum Source {
        SQL, FILE;

        ConfigStore open(Properties properties) {
            return switch (this) {
                case SQL -> {
                    Database.init(properties);
                    yield new PgConfigStore();
                }
                case FILE -> new FileConfigStore();
            };
        }

        // Documents missing from a new store are seeded from data/*.json, so existing setups migrate on first start.
        @Nullable ConfigStore importFrom() {
            return this == FILE ? null : new FileConfigStore();
        }

        static Source from(Properties properties) {
            String value = properties.getProperty("CONFIG_SOURCE", "").trim();
            if (value.isEmpty()) {
                return properties.getProperty("SQL_LINK", "").isBlank() ? FILE : SQL;
            }
            return switch (value.toLowerCase()) {
                case "sql" -> SQL;
                case "file" -> FILE;
                default -> throw new IllegalStateException("CONFIG_SOURCE must be 'sql' or 'file', got '" + value + "'");
            };
        }
    }

    public Configurator(Bot bot) {
        this.bot = bot;
        try {
            properties = new Properties();
            properties.load(new StringReader(Files.readString(Path.of("data/bot.properties"))));
        } catch (IOException | IllegalArgumentException e ) {
            throw new IllegalStateException("Required files in data directory could not be loaded or parsed: bot.properties");
        }
        this.source = Source.from(properties);
        this.store = source.open(properties);
        ConfigService.init(store);
        Log.info("Configuration source: {}", source);

        ConfigStore importFrom = source.importFrom();
        for (Config file : Config.values()) {
            JSONObject document = Config.loadResource(file, store, importFrom);
            if (document == null) {
                if (file.required()) throw new IllegalStateException("Required configuration could not be loaded: " + file.fileName());
                continue;
            }
            documents.put(file, document);
            ConfigService.register(file, document);
        }
    }

    public @NotNull Properties properties() {
        return properties;
    }

    // Null only for optional documents that failed to load.
    public @Nullable JSONObject get(Config file) {
        return documents.get(file);
    }

    public @NotNull JSONObject require(Config file) {
        JSONObject document = documents.get(file);
        if (document == null) throw new IllegalStateException(file.fileName() + " is not loaded");
        return document;
    }
}
