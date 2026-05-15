package org.backblue.core;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.backblue.commands.Features;
import org.backblue.commands.Ping;
import org.backblue.events.DM;
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

    public Bot() throws IOException {
        features = EnumSet.noneOf(FeatureFlag.class);
        Properties keys = new Properties();
        keys.load(new StringReader(Files.readString(Path.of("data/bot.properties"))));
        JSONObject settings = new JSONObject(Files.readString(Path.of("data/settings.json")));
        JSONObject featuresList = new JSONObject(Files.readString(Path.of("data/features.json")));

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.create(keys.getProperty("TOKEN"), EnumSet.allOf(GatewayIntent.class));
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(EnumSet.allOf(CacheFlag.class));
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.fromKey(settings.getJSONObject("self").optString("presence", "online")));
        if (settings.getJSONObject("self").optString("presence", null) != null) {
            builder.setActivity(Activity.customStatus(settings.getJSONObject("self").getString("presence")));
        }

        io = new IO(settings.optJSONObject("channels", null));
        builder.addEventListeners(io, new Setup(io, settings.optJSONObject("channels", null)));
        builder.addEventListeners(new Ping(), new Features(this));
        builder.addEventListeners(new DM(this));

        if (featuresList.optBoolean("enforceGuideAccess", false)) features.add(FeatureFlag.EnforceOneGuideAccess);
        if (featuresList.optBoolean("blueSky", false)) features.add(FeatureFlag.BlueSky);
        if (featuresList.optBoolean("honeypot", false)) features.add(FeatureFlag.Honeypot);
        if (featuresList.optBoolean("securityActions", false)) features.add(FeatureFlag.SecurityActions);
        if (featuresList.optBoolean("autoModAlerts", false)) features.add(FeatureFlag.AutoModAlerts);
        if (featuresList.optBoolean("roleIcons", false)) features.add(FeatureFlag.RoleIcons);
        if (featuresList.optBoolean("msgForward", false)) features.add(FeatureFlag.MessageForwarding);

        this.JDA = builder.build();
    }

    public boolean featureEnabled(FeatureFlag feature) {
        return features.contains(feature);
    }
    public void enableFeature(FeatureFlag feature) {
        features.add(feature);
    }
    public void disableFeature(FeatureFlag feature) {
        features.remove(feature);
    }
    public FeatureFlag getFeature(double ordinal) {
        for (FeatureFlag feature : FeatureFlag.values()) {
            if (feature.ordinal() == ordinal) return feature;
        }
        return null;
    }
    public IO getIO() {
        return this.io;
    }
}
