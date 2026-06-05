package org.backblue.core;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.backblue.commands.*;
import org.backblue.events.*;
import org.backblue.utilities.*;
import org.backblue.wrappers.BlueSky;
import org.backblue.wrappers.Gemini;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class Bot {

    public final int major = 0;
    public final int minor = 9;
    public final int patch = 4;

    private static final Logger Log = LoggerFactory.getLogger(Bot.class);

    private final ShardManager JDA;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final MessageIO io;
    private final Gemini gemini;

    private final EnumSet<FeatureFlag> features;
    private final String deploymentGuildID;
    private final String pingRoleID;

    public Bot() throws IOException {
        Properties keys = new Properties();
        JSONObject settings = null;
        JSONObject badges = null;
        JSONObject rulebook = null;
        JSONObject featuresList = null;
        JSONObject settingSelf = null;
        try {
            keys.load(new StringReader(Files.readString(Path.of("data/bot.properties"))));
            settings = new JSONObject(Files.readString(Path.of("data/settings.json")));
            rulebook = new JSONObject(Files.readString(Path.of("data/rulebook.json")));
            badges = new JSONObject(Files.readString(Path.of("data/badges.json")));
            featuresList = new JSONObject(Files.readString(Path.of("data/features.json")));
            settingSelf = settings.getJSONObject("self");
            settings.getJSONObject("channels").getString("_deploy");

        } catch (IOException e) {
            Log.error("Cannot read files: data/bot.properties, data/settings.json, data/rulebook.json, data/badges.json, data/features.json");
            Log.error("settings.json also requires JSON objects attached to keys 'self', 'channels");
            System.exit(1);
        }
        this.deploymentGuildID = settings.getJSONObject("channels").getString("_deploy");
        this.pingRoleID = settingSelf.optString("pingAlerts", null);

        features = EnumSet.noneOf(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            try {
                if (featuresList.getBoolean(flag.configKey())) this.features.add(flag);
            } catch (JSONException e) {
                Log.warn("No setting found for '{}', turning off {}", flag.configKey(), flag);
            }
        }
        Log.info("{} features successfully enabled", features.size());

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.create(keys.getProperty("TOKEN"), EnumSet.allOf(GatewayIntent.class));
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(EnumSet.allOf(CacheFlag.class));
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.fromKey(settings.getJSONObject("self").optString("presence", "online")));
        if (settings.getJSONObject("self").optString("presence", null) != null) {
            builder.setActivity(Activity.customStatus(settings.getJSONObject("self").getString("status")));
        }

        io = new MessageIO(settings, this);
        gemini = new Gemini(this, keys.getProperty("GEMINI_TOKEN", null), settings.optJSONObject("gemini", null));
        Badge badge = new Badge(this, badges);
        builder.addEventListeners(io, new Setup(io, settings.optJSONObject("channels", null)));
        builder.addEventListeners(new Ping(), new Features(this), new AutoMod(this));
        builder.addEventListeners(new DM(this),
                new EZPunish(this, rulebook),
                new DisableDM(this),
                badge,
                new RaidProtect(this),
                new About(this, settingSelf.optString("watermark", "")));

        new BlueSky(keys.getProperty("BSKY_USER", null),
                keys.getProperty("BSKY_PASSWORD", null),
                settings.getJSONObject("blueSky"),
                this);
        this.JDA = builder.build();
    }

    public boolean isFeatureEnabled(FeatureFlag feature) {
        return features.contains(feature);
    }
    public void enableFeature(FeatureFlag feature) {
        features.add(feature);
    }
    public void disableFeature(FeatureFlag feature) {
        features.remove(feature);
    }
    public FeatureFlag getFeature(String ordinal) {
        for (FeatureFlag feature : FeatureFlag.values()) {
            if (feature.ordinal() == Integer.parseInt(ordinal)) return feature;
        }
        return null;
    }
    public MessageIO getIO() {
        return this.io;
    }
    public ShardManager getJDA() {
        return this.JDA;
    }
    public Guild getDeploymentGuild() {
        return JDA.getGuildById(this.deploymentGuildID);
    }
    public Role getMostModerators() {
        return this.getJDA().getRoleById(this.pingRoleID);
    }
    public ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }
    public EnumSet<FeatureFlag> getFeatures() {
        return features;
    }
    public Gemini getGemini() {
        return gemini;
    }
}
