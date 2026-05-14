package org.backblue;

import com.azure.ai.contentsafety.ContentSafetyClient;
import com.azure.ai.contentsafety.ContentSafetyClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.backblue.commands.*;
import org.backblue.commands.Module;
import org.backblue.events.*;
import org.backblue.utilities.ComponentManager;
import org.backblue.utilities.ContextManager;
import org.backblue.utilities.ModalManager;
import org.backblue.utilities.NdemicModule;
import org.backblue.wrappers.BlueSkyBot;
import org.backblue.wrappers.MessageHandler;
import org.backblue.wrappers.ProfileHandler;
import org.backblue.wrappers.SentinelManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.backblue.commands.EZPunish.generatePunishEmbed;
import static org.backblue.commands.EZPunish.logToWarnings;

public class Bot {
    public static final String VERSION = "0.8.0_c";
    public static final long BOOT = Instant.now().getEpochSecond();
    private final Properties keys;
    private JSONObject settings;
    private JSONObject modules;
    private JSONObject tasks;
    private final ShardManager shardManager;
    private static Bot botStatic;
    private final HashMap<String, String> analysis;
    private final HashMap<String, String> deployment;
    private final SentinelManager sentinelManager;
    private final Badge badges;

    private final ContentSafetyClient contentSafetyClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<NdemicModule> ndemicModules = new HashSet<>();

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }
    public ContentSafetyClient getContentSafetyClient() {
        return contentSafetyClient;
    }

    private Bot() {
        botStatic = this;
        JSONObject badgesData = null;
        try {
            settings = new JSONObject(new JSONTokener(Files.readString(Path.of("data/settings.json"))));
            modules = new JSONObject(new JSONTokener(Files.readString(Path.of("data/modules.json"))));
            tasks = new JSONObject(new JSONTokener(Files.readString(Path.of("data/tasks.json"))));
            badgesData = new JSONObject(new JSONTokener(Files.readString(Path.of("data/badges.json"))));
        } catch (IOException e) {
            System.err.println("Need config files: data/settings.json, data/modules.json, data/tasks.json, data/badges.json");
            System.exit(1);
        }
        keys = loadKeys();
        analysis = generateAnalysis();
        deployment = generateDeployment();
        MessageForwarder forwarder = new MessageForwarder(settings.getJSONArray("messageForwarding"));
        sentinelManager = new SentinelManager(settings.getJSONObject("sentinel"));
        registerNdemicModule(forwarder);
        registerNdemicModule(sentinelManager);
        registerNdemicModule(new ProfileHandler());
        registerNdemicModule(new MessageHandler());
        registerNdemicModule(new BlueSkyBot(keys.getProperty("BSKY_USER", null),
                keys.getProperty("BSKY_PASSWORD", null),
                getSettings().getJSONObject("bSky"),
                keys.getProperty("BSKY_FOOTER_TEXT", "BlueSky"),
                keys.getProperty("BSKY_FOOTER_ICON", "https://i.imgur.com/8iz3PZJ.jpeg")));
        try {
            Connection test = DriverManager.getConnection(keys.getProperty("JDBC"));
            test.close();
        } catch (SQLException e) {
            System.err.println("\u001B[0mSQL configuration is required!\n" + e);
            System.exit(2);
        }

        contentSafetyClient = new ContentSafetyClientBuilder()
                .endpoint(keys.getProperty("AZURE_SAFETY_ENDPOINT"))
                .credential(new AzureKeyCredential(keys.getProperty("AZURE_SAFETY_KEY")))
                .buildClient();

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.create(keys.getProperty("TOKEN"), EnumSet.allOf(GatewayIntent.class));
        builder.setActivity(Activity.customStatus(settings.getString("status")));
        int color = settings.getInt("presence");
        if (color == 1) {
            builder.setStatus(OnlineStatus.IDLE);
        } else if (color == 2) {
            builder.setStatus(OnlineStatus.DO_NOT_DISTURB);
        } else if (color == 3) {
            builder.setStatus(OnlineStatus.INVISIBLE);
        } else {
            builder.setStatus(OnlineStatus.ONLINE);
        }
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(EnumSet.allOf(CacheFlag.class));

        this.badges = new Badge(badgesData);
        builder.addEventListeners(new CommandList(), new ComponentManager(), new ModalManager(), new ContextManager());
        builder.addEventListeners(new Security(), new Ping(), new Uptime(), new Data(), new Module(), new EZPunish(), new Terminate(),
                this.badges,
                new Purge());
        builder.addEventListeners(
                new EnforceSecurityGatekeeper(sentinelManager),
                new EnforceSecurityOnboardCompletion(),
                forwarder,
                new RestrictedChannel(),
                new EnforceProfileScan(),
                new PrivateMessage(),
                new EnforceFanRole(),
                new EnforceOneOP(),
                new AutoModAlert(),
                new EnforceMessageScan());

        shardManager = builder.build();
    }

    public Set<NdemicModule> getNdemicModules() {
        return this.ndemicModules;
    }

    public void registerNdemicModule(NdemicModule module) {
        for (NdemicModule mod : Bot.getBot().getNdemicModules()) {
            if (mod.equals(module)) {
                this.ndemicModules.remove(mod);
            }
        }
        this.ndemicModules.add(module);
    }

    private static Properties loadKeys() {
        Properties SECURE_KEYS = new Properties();
        try {
            String rawKey = Files.readString(Path.of("data/keys.properties"));
            SECURE_KEYS.load(new java.io.StringReader(rawKey));
        } catch (IOException e) {
            System.err.println("Keys cannot be loaded. Ensure the file exists and is accessible.");
            System.exit(1);
        }
        return SECURE_KEYS;
    }
    private HashMap<String, String> generateAnalysis() {
        HashMap<String, String> map = new HashMap<>();
        for (String key : settings.getJSONObject("analysis").keySet()) {
            map.put(key, settings.getJSONObject("analysis").getString(key));
        }
        return map;
    }

    private HashMap<String, String> generateDeployment() {
        HashMap<String, String> map = new HashMap<>();
        for (String key : settings.getJSONObject("deployment").keySet()) {
            if (key.equals("guild") || key.contains("FailureMsg")) {
                map.put(key, settings.getJSONObject("deployment").getString(key));
            } else if (key.contains("List")) {
                JSONArray arr = settings.getJSONObject("deployment").getJSONArray(key);
                for (int i = 0; i < arr.length(); i++) {
                    map.put(key + "." + i, arr.getString(i));
                }
                map.put(key + ".size", String.valueOf(arr.length()));
            } else {
                for (String anotherKey : settings.getJSONObject("deployment").getJSONObject(key).keySet()) {
                    map.put(key + "." + anotherKey, settings.getJSONObject("deployment").getJSONObject(key).getString(anotherKey));
                }
            }
        }
        return map;
    }

    public Guild getDeploymentGuild() {
        return Bot.getBot().getJDA().getGuildById(Bot.getBot().getDeployment().get("guild"));
    }

    public ShardManager getJDA() {
        return shardManager;
    }
    public JSONObject getModules() {
        return modules;
    }
    public JSONObject getTasks() {
        return tasks;
    }
    public JSONObject getSettings() {
        return settings;
    }
    public SentinelManager getSentinelManager() {
        return sentinelManager;
    }
    public Badge getBadgeSystem() {
        return this.badges;
    }

    public boolean getModuleValue(String key) {
        if (modules.has(key)) {
            return modules.getJSONObject(key).getBoolean("enabled");
        }
        return false;
    }
    public @Nullable String getModuleDescription(String key) {
        if (modules.has(key)) {
            return modules.getJSONObject(key).getString("description");
        }
        return null;
    }
    public HashMap<String, String> getAnalysis() {
        return analysis;
    }
    public HashMap<String, String> getDeployment() {
        return deployment;
    }
    public String getSQL() {
        return keys.getProperty("JDBC");
    }
    public static Bot getBot() {
        return botStatic;
    }

    public boolean sendTextChannelMessage(String id, String txt, MessageEmbed message) {
        Channel channel = getJDA().getGuildChannelById(id);
        if (channel != null) {
            switch (channel.getType()) {
                case TEXT -> ((TextChannel) channel).sendMessage(txt).setEmbeds(message).queue();
                case NEWS -> ((NewsChannel) channel).sendMessage(txt).setEmbeds(message).queue();
            }
            return true;
        }
        return false;
    }

    public boolean sendTextChannelMessage(String id, String txt) {
        Channel channel = getJDA().getGuildChannelById(id);
        if (channel != null) {
            switch (channel.getType()) {
                case TEXT -> ((TextChannel) channel).sendMessage(txt).queue();
                case NEWS -> ((NewsChannel) channel).sendMessage(txt).queue();
            }
            return true;
        }
        return false;
    }

    public void sendDebugMessage(String type, String message) {
        if (getModuleValue("analytics")) {
            TextChannel analysisChannel = getJDA().getTextChannelById(getAnalysis().get(type));
            if (analysisChannel != null) {
                analysisChannel.sendMessage(message).queue();
            }
        }
    }

    public void sendDebugMessage(String type, String message, FileUpload attachment) {
        if (getModuleValue("analytics")) {
            TextChannel analysisChannel = getJDA().getTextChannelById(getAnalysis().get(type));
            if (analysisChannel != null) {
                analysisChannel.sendMessage(message).addFiles(attachment).queue();
            }
        }
    }

    public void sendDebugMessage(String type, MessageEmbed embed) {
        if (getModuleValue("analytics")) {
            TextChannel analysisChannel = getJDA().getTextChannelById(getAnalysis().get(type));
            if (analysisChannel != null) {
                analysisChannel.sendMessageEmbeds(embed).queue();
            }
        }
    }

    public void sendDeploymentMessage(String type, String message, MessageEmbed embed) {
        String channelId = getDeployment().get("channels." + type);
        GuildChannel analysisChannel = getJDA().getGuildChannelById(channelId);
        if (analysisChannel != null) {
            switch (analysisChannel.getType()) {
                case TEXT -> ((TextChannel) analysisChannel).sendMessage(message).setEmbeds(embed).queue();
                case NEWS -> ((NewsChannel) analysisChannel).sendMessage(message).setEmbeds(embed).queue();
            }
        }
    }
    public void sendDeploymentMessage(String type, String message, MessageEmbed embed, List<FileUpload> uploads) {
        String channelId = getDeployment().get("channels." + type);
        GuildChannel analysisChannel = getJDA().getGuildChannelById(channelId);
        if (analysisChannel != null) {
            switch (analysisChannel.getType()) {
                case TEXT -> ((TextChannel) analysisChannel).sendMessage(message).addFiles(uploads).setEmbeds(embed).queue();
                case NEWS -> ((NewsChannel) analysisChannel).sendMessage(message).addFiles(uploads).setEmbeds(embed).queue();
            }
        }
    }

    public GenerateContentResponse gemini(String input, GenerateContentConfig config) {
        JSONArray array = Bot.getBot().getSettings().getJSONObject("gemini").getJSONArray("priorities");
        for (int i = 0; i < array.length(); i++) {
            String model = array.getString(i);
            try (Client client = Client.builder().apiKey(keys.getProperty(model)).build()) {
                return client.models.generateContent(
                        keys.getProperty("GEMINI_TOKEN"),
                        input,
                        config);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void sendDeploymentMessage(String type, String message) {
        TextChannel analysisChannel = getJDA().getTextChannelById(getDeployment().get("channels." + type));
        if (analysisChannel != null) {
            analysisChannel.sendMessage(message).queue();
        }
    }

    public void sendDeploymentMessage(String type, String message, FileUpload... attachment) {
        TextChannel analysisChannel = getJDA().getTextChannelById(getDeployment().get("channels." + type));
        if (analysisChannel != null) {
            analysisChannel.sendMessage(message).addFiles(attachment).queue();
        }
    }

    public void sendUserMessage(User user, String message) {
        user.openPrivateChannel()
                .queue(privateChannel -> privateChannel.sendMessage(message).queue());
    }

    public void sendUserMessage(User user, MessageEmbed embed) {
        user.openPrivateChannel()
                .queue(privateChannel -> privateChannel.sendMessageEmbeds(embed).queue());
    }

    public @NotNull Role getMostModerators() {
        return Objects.requireNonNull(getJDA().getRoleById(getDeployment().get("roles.optIn").toLowerCase()));
    }

    public void additionalReview(Member member, boolean timeout, ComponentManager.ComponentPreset preset, String... evidence) {
        if (timeout) {
            Bot.getBot().sendUserMessage(member.getUser(), "Hello, your recent activity has been flagged for additional review by our moderators. You have been temporarily timed out for 6 hours as we review this situation. We apologize for the inconvenience.");
            member.timeoutFor(6, TimeUnit.HOURS).queue();
        }
        ComponentManager.EZPunishComponentInteractionEvent event = switch (preset) {
            case MESSAGE -> ComponentManager.message(member, evidence[0]);
            case PROFILE_PICTURE -> ComponentManager.profilePicture(member, evidence[0], evidence[1]);
            case CUSTOM_STATUS -> ComponentManager.customStatus(member, evidence[0]);
            case OTHER -> ComponentManager.other(member, evidence);
            case BANNER -> ComponentManager.banner(member, evidence[0], evidence[1]);
        };

        Guild guild = member.getGuild();
        TextChannel channel = guild.getTextChannelById(getDeployment().get("channels.cmd"));
        if (event != null && channel != null) {
            channel.sendMessage(getMostModerators().getAsMention()).queue();
            channel.sendMessageComponents(event.container()).useComponentsV2(true).queue();
        }
    }

    public EZPunish.EZPunishResult ezPunish(Member target, Member executor, List<String> violations, boolean ban, String evidenceText, List<Message.Attachment> evidenceImages, String notes) {
        if (target == null || executor == null) {
            return new EZPunish.EZPunishResult(false, "Invalid targets provided.");
        }
        if (!executor.hasPermission(Permission.BAN_MEMBERS)) {
            return new EZPunish.EZPunishResult(false, "Executor lacks permissions.");
        }
        if (evidenceText == null && evidenceImages == null) {
            return new EZPunish.EZPunishResult(false, "No evidence provided.");
        }
        if (target.hasPermission(Permission.BAN_MEMBERS)) {
            return new EZPunish.EZPunishResult(false, "Target has Administrator permissions.");
        }
        Bot.getBot().sendUserMessage(target.getUser(), generatePunishEmbed(violations, target, ban, notes, target.getGuild().getIconUrl()));
        logToWarnings(violations, target, ban, evidenceText, evidenceImages, executor);
        target.ban(1, TimeUnit.HOURS).reason("Moderator initiated by " + executor.getUser().getName()).queue();
        Bot.getBot().getScheduler().schedule(() -> {
            if (!ban) {
                target.getGuild().unban(target).queue();
            }
        }, 4, TimeUnit.SECONDS);
        return new EZPunish.EZPunishResult(true, "User has been " + (ban ? "banned" : "removed") + " and logged.");
    }

    public EZPunish.EZPunishResult ezPunish(Member target, Member executor, List<String> violations, boolean ban, String evidenceText, Message.Attachment evidenceImage) {
        if (evidenceImage == null) {
            return ezPunish(target, executor, violations, ban, evidenceText, null, null);
        }
        return ezPunish(target, executor, violations, ban, evidenceText, List.of(evidenceImage), null);
    }

    public void purgeMessages(Member member, int hours) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(hours);
        AtomicInteger remaining = new AtomicInteger(100);

        List<GuildMessageChannel> channels = new ArrayList<>();
        channels.addAll(member.getGuild().getTextChannels());
        channels.addAll(member.getGuild().getThreadChannels());

        for (GuildMessageChannel channel : channels) {
            if (remaining.get() <= 0)
                break;

            if (!member.hasPermission(channel, Permission.VIEW_CHANNEL))
                continue;

            channel.getHistory().retrievePast(100).queue(messages -> {

                List<Message> toDelete = new ArrayList<>();

                for (Message m : messages) {
                    if (remaining.get() <= 0)
                        break;

                    if (!m.getAuthor().getId().equals(member.getId()))
                        continue;

                    if (m.getTimeCreated().isBefore(cutoff))
                        continue;

                    toDelete.add(m);
                    remaining.decrementAndGet();
                }

                if (toDelete.size() >= 2) {
                    if (channel instanceof TextChannel tc) {
                        tc.deleteMessages(toDelete).queue();
                    } else if (channel instanceof ThreadChannel thread) {
                        toDelete.forEach(m -> m.delete().queue());
                    } else {
                        toDelete.forEach(m -> m.delete().queue());
                    }
                }
                else if (toDelete.size() == 1) {
                    toDelete.getFirst().delete().queue();
                }
            });
        }
    }

    public static void main(String[] args) {
        new Bot();
    }
}