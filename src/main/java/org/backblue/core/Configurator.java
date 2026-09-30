package org.backblue.core;

import org.backblue.enums.Config;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public final class Configurator {

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
            throw new IllegalStateException("Required files in data directory could not be loaded or parsed: bot.properties");
        }

        this.settingsFile = Objects.requireNonNull(Config.loadResource(Config.Settings_JSON));
        this.badgesFile = Objects.requireNonNull(Config.loadResource(Config.Badges_JSON));
        this.rulebookFile = Objects.requireNonNull(Config.loadResource(Config.Rulebook_JSON));
        this.featuresFile = Objects.requireNonNull(Config.loadResource(Config.Features_JSON));
        this.deploymentAuditFile = Config.loadResource(Config.Deployment_Audit_JSON);
        this.deploymentAutoresponderFile = Config.loadResource(Config.Deployment_Triggers_JSON);
    }
}
