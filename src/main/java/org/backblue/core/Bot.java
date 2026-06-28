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
import org.backblue.moderation.*;
import org.backblue.utilities.*;
import org.backblue.utilities.BlueSky;
import org.backblue.moderation.ProfileScan;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Bot {

    public final int major = 0;
    public final int minor = 9;
    public final int patch = 6;

    private static final Logger Log = LoggerFactory.getLogger(Bot.class);

    private final ShardManager JDA;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final MessageIO io;
    private final GenAI ai;
    private final Interactive interactive;

    private final EnumSet<FeatureFlag> features;
    private final String deploymentGuildID;
    private final String pingRoleID;
    private final String debugPingRoleID;

    public Bot(String... args) {
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
        new Debug(this, Set.of(args));

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

        io = new MessageIO(settings, this);
        ai = new GenAI(this, keys.getProperty("GEMINI_TOKEN", null), settings.optJSONObject("gemini", null));
        EZPunish ezp = new EZPunish(this, rulebook);
        interactive = new Interactive(this, ezp);
        ProfileScan profileScan = new ProfileScan(this, this.interactive, keys.getProperty("AZURE_SAFETY_ENDPOINT", null), keys.getProperty("AZURE_SAFETY_KEY", null), settings.optJSONObject("profileScanner"));
        builder.addEventListeners(new DM(this),
                new Ping(), new Features(this), new AutoMod(this),
                this.io, new Deployment(this.io, settings.optJSONObject("channels", null)),
                ezp,
                profileScan,
                this.interactive,
                new DisableDM(this),
                new Scan(this, profileScan),
                new Badge(this, badges),
                new RaidProtect(this),
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
    public Role getDebugPing() {
        return this.getJDA().getRoleById(this.debugPingRoleID);
    }
    public Interactive getInteractive() {
        return this.interactive;
    }
    public ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }
    public void timeout(Member member, String reason, int duration, TimeUnit unit) {
        if (member == null) return;
        OffsetDateTime currentTimeout = member.getTimeOutEnd();
        OffsetDateTime newTimeout = OffsetDateTime.now().plus(duration, unit.toChronoUnit());
        if (currentTimeout == null || currentTimeout.isBefore(OffsetDateTime.now())) {
            if (newTimeout.isAfter(OffsetDateTime.now().plusDays(27))) newTimeout = OffsetDateTime.now().plusDays(27);
            member.timeoutUntil(newTimeout).reason(reason).queue();
        } else {
            currentTimeout = currentTimeout.plus(duration, unit.toChronoUnit());
            if (currentTimeout.isAfter(OffsetDateTime.now().plusDays(27))) currentTimeout = OffsetDateTime.now().plusDays(27);
            member.timeoutUntil(currentTimeout).reason(reason).queue();
        }

        member.timeoutFor(duration, unit).reason(reason).queue();
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
    public String readResource(String path) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new FileNotFoundException(path);
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            Log.error("Error reading prompt: {}", e.getMessage());
            return null;
        }
    }
}
