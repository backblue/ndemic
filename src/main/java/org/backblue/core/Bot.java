package org.backblue.core;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.backblue.commands.About;
import org.backblue.commands.Badge;
import org.backblue.commands.Features;
import org.backblue.commands.Ping;
import org.backblue.events.AutoMod;
import org.backblue.events.DM;
import org.backblue.events.Modal;
import org.backblue.utilities.FeatureFlag;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;

public final class Bot {

    public final int major = 0;
    public final int minor = 9;
    public final int patch = 1;

    private final ShardManager JDA;
    private final IO io;
    private final EnumSet<FeatureFlag> features;
    private final String deploymentGuildID;
    private final String debugGuildID;
    public final String pingRoleID;

    public Bot() throws IOException {
        features = EnumSet.noneOf(FeatureFlag.class);
        Properties keys = new Properties();
        keys.load(new StringReader(Files.readString(Path.of("data/bot.properties"))));
        JSONObject settings = new JSONObject(Files.readString(Path.of("data/settings.json")));
        JSONObject badges = new JSONObject(Files.readString(Path.of("data/badges.json")));
        JSONObject featuresList = new JSONObject(Files.readString(Path.of("data/features.json")));
        JSONObject settingSelf = settings.optJSONObject("self", null);
        this.deploymentGuildID = settings.getJSONObject("channels").getString("_deploy");
        this.debugGuildID = settings.getJSONObject("channels").getString("_debug");
        this.pingRoleID = settingSelf.optString("pingAlerts", null);

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.create(keys.getProperty("TOKEN"), EnumSet.allOf(GatewayIntent.class));
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(EnumSet.allOf(CacheFlag.class));
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.fromKey(settings.getJSONObject("self").optString("presence", "online")));
        if (settings.getJSONObject("self").optString("presence", null) != null) {
            builder.setActivity(Activity.customStatus(settings.getJSONObject("self").getString("presence")));
        }

        io = new IO(settings.optJSONObject("channels", null), this);
        Badge badge = new Badge(this, badges);
        builder.addEventListeners(io, new Setup(io, settings.optJSONObject("channels", null)));
        builder.addEventListeners(new Ping(), new Features(this), new AutoMod(this, settingSelf.optString("pingAlerts", null)));
        builder.addEventListeners(new DM(this), badge, new Modal(badge), new About(this, pingRoleID));

        if (featuresList.optBoolean("enforceGuideAccess", false)) features.add(FeatureFlag.EnforceOneGuideAccess);
        if (featuresList.optBoolean("blueSky", false)) features.add(FeatureFlag.BlueSky);
        if (featuresList.optBoolean("honeypot", false)) features.add(FeatureFlag.Honeypot);
        if (featuresList.optBoolean("securityActions", false)) features.add(FeatureFlag.SecurityActions);
        if (featuresList.optBoolean("autoModAlerts", false)) features.add(FeatureFlag.AutoModAlerts);
        if (featuresList.optBoolean("roleIcons", false)) features.add(FeatureFlag.RoleIcons);
        if (featuresList.optBoolean("msgForward", false)) features.add(FeatureFlag.MessageForwarding);

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
    public IO getIO() {
        return this.io;
    }
    public ShardManager getJDA() {
        return this.JDA;
    }
    public static String formatTimeShort(Long seconds) {
        if (seconds <= 0) {
            return "now";
        }

        String returnString = null;

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) {
            returnString = days + "d " + hours + "h " + minutes + "m " + secs + "s";
        } else if (hours > 0) {
            returnString = hours + "h " + minutes + "m " + secs + "s";
        } else if (minutes > 0) {
            returnString = minutes + "m " + secs + "s";
        } else if (secs > 0) {
            returnString = secs + "s";
        }
        return returnString;
    }
    public Guild getDeploymentGuild() {
        return JDA.getGuildById(this.deploymentGuildID);
    }
    public Guild getDebugGuild() {
        return JDA.getGuildById(this.debugGuildID);
    }
}
