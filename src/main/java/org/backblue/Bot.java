package org.backblue;

import com.azure.ai.contentsafety.ContentSafetyClient;
import com.azure.ai.contentsafety.ContentSafetyClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.backblue.commands.*;
import org.backblue.commands.Module;
import org.backblue.events.*;
import org.backblue.tasks.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;

public class Bot {
    public static final String VERSION = "0.6.0";
    public static final long BOOT = Instant.now().getEpochSecond();
    private final Properties keys;
    private final JSONObject settings;
    private final JSONObject modules;
    private final JSONObject tasks;
    private final ShardManager shardManager;
    private static Bot botStatic;
    private final HashMap<String, String> analysis;
    private final HashMap<String, String> deployment;

    private final ContentSafetyClient contentSafetyClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<Task> taskqueue;
    private final Stack<Task> completedTasks;

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }
    public ContentSafetyClient getContentSafetyClient() {
        return contentSafetyClient;
    }

    public Bot() throws IOException {
        botStatic = this;

        keys = loadKeys();
        settings = new JSONObject(new JSONTokener(Files.readString(Path.of("data/settings.json"))));
        modules = new JSONObject(new JSONTokener(Files.readString(Path.of("data/modules.json"))));
        analysis = generateAnalysis();
        deployment = generateDeployment();
        tasks = new JSONObject(new JSONTokener(Files.readString(Path.of("data/tasks.json"))));
        taskqueue = new LinkedBlockingDeque<>();
        AutoModAlert.generateStaticVariables();
        completedTasks = new Stack<>();

        try {
            Connection test = DriverManager.getConnection(keys.getProperty("JDBC"));
            test.close();
        } catch (SQLException e) {
            System.out.println("\u001B[0mSQL configuration is required!\n" + e);
            System.exit(1);
        }

        contentSafetyClient = new ContentSafetyClientBuilder()
                .endpoint(keys.getProperty("AZURE_SAFETY_ENDPOINT"))
                .credential(new AzureKeyCredential(keys.getProperty("AZURE_SAFETY_KEY")))
                .buildClient();

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.create(keys.getProperty("TOKEN"), EnumSet.allOf(GatewayIntent.class));
        builder.setActivity(Activity.customStatus(settings.getString("status")));
        builder.setStatus(OnlineStatus.DO_NOT_DISTURB);
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        shardManager = builder.build();
    }

    private static Properties loadKeys() {
        Properties SECURE_KEYS = new Properties();
        try {
            String rawKey = Files.readString(Path.of("data/keys.properties"));
            SECURE_KEYS.load(new java.io.StringReader(rawKey));
        } catch (IOException e) {
            System.out.println("Keys cannot be loaded. Please ensure the file exists and is accessible.");
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

    public ShardManager getJDA() {
        return shardManager;
    }

    public JSONObject getModules() {
        return modules;
    }

    public JSONObject getTasks() {
        return tasks;
    }

    public Boolean getModuleValue(String key) {
        if (modules.has(key)) {
            return modules.getJSONObject(key).getBoolean("enabled");
        }
        return null;
    }

    public String getModuleDescription(String key) {
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

    public BlockingQueue<Task> getTaskQueue() {
        return taskqueue;
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

    public void sendDebugEmbed(String type, MessageEmbed embed) {
        if (getModuleValue("analytics")) {
            TextChannel analysisChannel = getJDA().getTextChannelById(getAnalysis().get(type));
            if (analysisChannel != null) {
                analysisChannel.sendMessageEmbeds(embed).queue();
            }
        }
    }

    public void sendDeploymentMessage(String type, String message) {
        TextChannel analysisChannel = getJDA().getTextChannelById(getDeployment().get("channels." + type));
        if (analysisChannel != null) {
            analysisChannel.sendMessage(message).queue();
        }
    }

    public void sendDeploymentMessage(String type, String message, FileUpload attachment) {
        TextChannel analysisChannel = getJDA().getTextChannelById(getDeployment().get("channels." + type));
        if (analysisChannel != null) {
            analysisChannel.sendMessage(message).addFiles(attachment).queue();
        }
    }

    public void sendUserMessage(User user, String message) {
        user.openPrivateChannel()
                .queue(privateChannel -> {
                    privateChannel.sendMessage(message).queue();
                });
    }

    public void sendUserMessage(User user, MessageEmbed embed) {
        user.openPrivateChannel()
                .queue(privateChannel -> {
                    privateChannel.sendMessageEmbeds(embed).queue();
                });
    }

    public @Nullable Task searchTask(int id) {
        return Task.IDS_TO_TASK.get(id);
    }

    public @Nullable EmbedBuilder taskToEmbed(int id) {
        Task task = searchTask(id);
        if (task == null) {
            return null;
        }
        HashMap<String, String> info = task.lookup();
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.YELLOW);
        embed.setTitle("Task `" + id + "`");
        for (String key : info.keySet()) {
            if (info.get(key) == null) {
                embed.addField(key, "N/A", true);
                continue;
            }
            embed.addField(key, info.get(key), true);
        }
        if (task.getStarted() > 0 && task.getFinished() > 0) {
            long waited = (task.getStarted() - task.getCreated())/1000;
            long elapsed = (task.getFinished() - task.getCreated())/1000;
            embed.setFooter("Waited " + waited + "s to run, Ran for " + elapsed + "s");
        }
        return embed;
    }

    public @NotNull Role getMostModerators() {
        return Objects.requireNonNull(getJDA().getRoleById(getDeployment().get("roles.optIn")));
    }

    public @NotNull Role getAllModerators() {
        return Objects.requireNonNull(getJDA().getRoleById(getDeployment().get("roles.all")));
    }

    public BlockingQueue<Task> getTaskqueue() {
        return taskqueue;
    }

    public Stack<Task> getCompletedTasks() {
        return completedTasks;
    }

    public static void main(String[] args) throws IOException {
        Bot bot = new Bot();
        bot.getJDA().addEventListener(new CommandList());
        bot.getJDA().addEventListener(new Ping(), new Uptime(), new Data(), new Module(), new Tasks());
        bot.getJDA().addEventListener(new EnforceProfileScan(), new PrivateMessage(), new EnforceFanRole(), new EnforceOneOP(), new AutoModAlert(), new EnforceMessageScan());

        Thread taskRunner = new Thread(() -> {
            while (true) {
                try {
                    Task task = bot.getTaskQueue().take();
                    task.process();
                    bot.completedTasks.push(task);
                    bot.sendDebugEmbed("tasks", bot.taskToEmbed(task.getId()).build());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        taskRunner.start();
    }

}