package org.backblue.core;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.backblue.commands.*;
import org.backblue.enums.Config;
import org.backblue.enums.Feature;
import org.backblue.moderation.*;
import org.backblue.utilities.*;
import org.backblue.utilities.BlueSky;
import org.backblue.cloud.ProfileScan;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Bot {

    public final int major = 1;
    public final int minor = 2;
    public final int patch = 1;
    public final long createdSince = OffsetDateTime.now().toEpochSecond();

    private static final Logger Log = LoggerFactory.getLogger(Bot.class);

    private final ShardManager JDA;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final MessageIO io;
    private final GenAI ai;
    private final EZPunish ezPunish;
    private final LiveContainer liveContainer;

    private final EnumSet<Feature> features;
    private final String deploymentGuildID;
    private final String mostModeratorsPing;
    private final String allModeratorsPing;
    private final String debugPingRoleID;

    public Bot(String... args) {

        Properties keys = new Properties();
        JSONObject settings = null;
        JSONObject badges = null;
        JSONObject rulebook = null;
        JSONObject featuresList = null;
        JSONObject settingSelf = null;

        Configurator config = null;

        try {
            config = new Configurator(this);
            keys = config.properties;
            settings = config.settingsFile;
            rulebook = config.rulebookFile;
            badges = config.badgesFile;
            featuresList = config.featuresFile;
            settingSelf = settings.getJSONObject("self");
            settings.getJSONObject("channels").getString("_deploy");
        } catch (IllegalStateException | Config.Error e) {
            Log.error("Required configuration could not be loaded", e);
            System.exit(1);
        }

        this.deploymentGuildID = settings.getJSONObject("channels").getString("_deploy");
        this.mostModeratorsPing = settingSelf.optString("pingAlerts", null);
        this.allModeratorsPing = settingSelf.optString("allPingAlerts", null);
        this.debugPingRoleID = settingSelf.optString("debugPingAlerts", null);

        features = EnumSet.noneOf(Feature.class);
        for (Feature flag : Feature.values()) {
            try {
                if (featuresList.getBoolean(flag.configKey())) this.features.add(flag);
            } catch (JSONException e) {
                Log.warn("No setting found for '{}', turning off feature {}", flag.configKey(), flag);
            }
        }
        Log.info("{} features successfully enabled", features.size());
        new MemoryDebug(this, Set.of(args));

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.create(keys.getProperty("TOKEN"), EnumSet.allOf(GatewayIntent.class));
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(EnumSet.allOf(CacheFlag.class));
        builder.disableCache(CacheFlag.ONLINE_STATUS);
        builder.disableCache(CacheFlag.ACTIVITY);
        builder.disableCache(CacheFlag.CLIENT_STATUS);
        builder.enableIntents(EnumSet.allOf(GatewayIntent.class));
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES);
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.fromKey(settings.getJSONObject("self").optString("presence", "online")));
        if (settings.getJSONObject("self").optString("presence", null) != null) {
            builder.setActivity(Activity.customStatus(settings.getJSONObject("self").getString("status")));
        }
        Autoresponding autoresponding = new Autoresponding(Integer.MAX_VALUE, this, config.deploymentAutoresponderFile);
        this.liveContainer = new LiveContainer(this);
        this.io = new MessageIO(settings, this, keys);
        this.io.addListener(autoresponding);
        this.ai = new GenAI(this, keys.getProperty("GEMINI_TOKEN", null), settings.optJSONObject("gemini", null));
        this.ezPunish = new EZPunish(this, rulebook);
        Auditing auditing = new Auditing(this, config.deploymentAuditFile);
        ProfileScan profileScan = new ProfileScan(this, keys.getProperty("AZURE_SAFETY_ENDPOINT", null), keys.getProperty("AZURE_SAFETY_KEY", null), settings.optJSONObject("profileScanner"));
        List<EventListener> listeners = List.of(
                new DM(this),
                new Ping(), new Features(this), new AutoMod(this),
                this.io,
                this.ezPunish,
                liveContainer,
                profileScan,
                auditing,
                new Autorespond(this, autoresponding),
                new DisableDM(this),
                new Scan(this, profileScan),
                new Badge(this, badges),
                new RaidProtect(this),
                new Audit(this, auditing),
                new Gatekeeper(this, settings.optJSONObject("gatekeeper")),
                new KickAll(this, settings.optJSONObject("gatekeeper")),
                new Privacy(this),
                new About(this, settingSelf.optString("watermark", ""))
        );
        builder.addEventListeners(Arrays.stream(listeners.toArray()).toList());
        builder.addEventListeners(new Deployment(settings.optJSONObject("channels", null), listeners));

        new BlueSky(keys.getProperty("BSKY_USER", null),
                keys.getProperty("BSKY_PASSWORD", null),
                settings.getJSONObject("blueSky"),
                this);
        this.JDA = builder.build();
    }

    public boolean isFeatureEnabled(Feature feature) {
        return features.contains(feature);
    }
    public void enableFeature(Feature feature) {
        features.add(feature);
    }
    public void disableFeature(Feature feature) {
        features.remove(feature);
    }
    public Feature getFeature(String ordinal) {
        for (Feature feature : Feature.values()) {
            if (feature.ordinal() == Integer.parseInt(ordinal)) return feature;
        }
        throw new RuntimeException("Unable to find feature with ordinal '" + ordinal + "'");
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
        return this.getJDA().getRoleById(this.mostModeratorsPing);
    }
    public Role getAllModerators() {
        return this.getJDA().getRoleById(this.allModeratorsPing);
    }
    public Role getDebugPing() {
        return this.getJDA().getRoleById(this.debugPingRoleID);
    }
    public EZPunish getEZPunish() {
        return this.ezPunish;
    }
    public ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }
    public LiveContainer getLiveContainer() {
        return this.liveContainer;
    }
    public void timeout(Member member, String reason, int duration, TimeUnit unit) {
        if (member == null) return;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime maxTimeout = now.plusDays(28).minusSeconds(1);
        OffsetDateTime timeoutEnd = member.getTimeOutEnd();

        if (timeoutEnd == null || timeoutEnd.isBefore(now)) timeoutEnd = now;
        timeoutEnd = timeoutEnd.plus(duration, unit.toChronoUnit());

        if (timeoutEnd.isAfter(maxTimeout)) timeoutEnd = maxTimeout;

        member.timeoutUntil(timeoutEnd).reason(reason).queue(
                success -> {},
                failure -> {}
        );
    }
    public EnumSet<Feature> getFeatures() {
        return features;
    }
    public GenAI getAI() {
        return ai;
    }
}
