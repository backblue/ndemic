package org.backblue.core;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.backblue.commands.*;
import org.backblue.core.containers.Interactive;
import org.backblue.core.containers.LiveContainer;
import org.backblue.enums.FeatureFlag;
import org.backblue.moderation.*;
import org.backblue.utilities.*;
import org.backblue.utilities.BlueSky;
import org.backblue.cloud.ProfileScan;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Bot {

    public final int major = 1;
    public final int minor = 2;
    public final int patch = 0;

    private static final Logger Log = LoggerFactory.getLogger(Bot.class);

    private final ShardManager JDA;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final MessageIO io;
    private final GenAI ai;
    private final Interactive interactive;
    private final LiveContainer liveContainer;

    private final EnumSet<FeatureFlag> features;
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
        } catch (Configurator.Error e) {
            System.exit(1);
        }

        this.deploymentGuildID = settings.getJSONObject("channels").getString("_deploy");
        this.mostModeratorsPing = settingSelf.optString("pingAlerts", null);
        this.allModeratorsPing = settingSelf.optString("allPingAlerts", null);
        this.debugPingRoleID = settingSelf.optString("debugPingAlerts", null);

        features = EnumSet.noneOf(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
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
        EZPunish ezp = new EZPunish(this, rulebook);
        interactive = new Interactive(this, ezp);
        Auditing auditing = new Auditing(this, config.deploymentAuditFile);
        ProfileScan profileScan = new ProfileScan(this, keys.getProperty("AZURE_SAFETY_ENDPOINT", null), keys.getProperty("AZURE_SAFETY_KEY", null), settings.optJSONObject("profileScanner"));
        builder.addEventListeners(new DM(this),
                new Ping(), new Features(this), new AutoMod(this),
                this.io, new Deployment(settings.optJSONObject("channels", null)),
                ezp,
                liveContainer,
                profileScan,
                this.interactive,
                auditing,
                new Autorespond(this, autoresponding),
                new DisableDM(this),
                new Scan(this, profileScan),
                new Badge(this, badges),
                new RaidProtect(this),
                new Audit(this, auditing),
                new Gatekeeper(this, settings.optJSONObject("gatekeeper")),
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
    public Interactive getInteractive() {
        return this.interactive;
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
    public EnumSet<FeatureFlag> getFeatures() {
        return features;
    }
    public GenAI getAI() {
        return ai;
    }
    public List<FileUpload> toUploads(List<Message.Attachment> attachmentList) {
        List<CompletableFuture<FileUpload>> futures = attachmentList.stream()
                .map(attachment ->
                        attachment.getProxy()
                                .download()
                                .thenApply(file ->
                                        FileUpload.fromData(file, attachment.getFileName())
                                )
                                .exceptionally(e -> {
                                    Log.error("Failed to download attachment: ", e);
                                    return null;
                                })
                )
                .toList();

        return futures.stream()
                .filter(Objects::nonNull)
                .map(CompletableFuture::join)
                .toList();

    }
    public String readResourceString(String path) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new FileNotFoundException(path);
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            Log.error("Error reading resource: {}", e.getMessage());
            return null;
        }
    }
    public String formattedTime(long seconds, boolean abbreviated, int maxUnits) {
        if (maxUnits <= 0) {
            return "";
        }

        long[] values = getValues(seconds);

        String[] longNames = {
                "year", "month", "week", "day", "hour", "minute", "second"
        };

        String[] shortNames = {"y", "mo", "w", "d", "h", "m", "s"};
        StringBuilder sb = new StringBuilder();
        int unitsAdded = 0;

        for (int i = 0; i < values.length && unitsAdded < maxUnits; i++) {
            long value = values[i];

            if (value == 0 && (unitsAdded > 0 || i != values.length - 1)) {
                continue;
            }

            if (abbreviated) {
                sb.append(String.format("%02d%s", value, shortNames[i]));
            } else {
                if (unitsAdded > 0) {
                    sb.append(", ");
                }
                sb.append(value)
                        .append(" ")
                        .append(longNames[i])
                        .append(value == 1 ? "" : "s");
            }

            unitsAdded++;
        }

        return sb.toString();
    }

    private static long @NonNull [] getValues(long seconds) {
        final long SECOND = 1;
        final long MINUTE = 60 * SECOND;
        final long HOUR = 60 * MINUTE;
        final long DAY = 24 * HOUR;
        final long WEEK = 7 * DAY;
        final long MONTH = 30 * DAY;
        final long YEAR = 365 * DAY;

        return new long[]{
                seconds / YEAR,
                (seconds % YEAR) / MONTH,
                (seconds % MONTH) / WEEK,
                (seconds % WEEK) / DAY,
                (seconds % DAY) / HOUR,
                (seconds % HOUR) / MINUTE,
                seconds % MINUTE
        };
    }

    public String formattedTime(long seconds, boolean abbreviated) {
        return formattedTime(seconds, abbreviated, Integer.MAX_VALUE);
    }
}
