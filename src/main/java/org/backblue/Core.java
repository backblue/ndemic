package org.backblue;

import com.azure.ai.contentsafety.ContentSafetyClient;
import com.azure.ai.contentsafety.ContentSafetyClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.backblue.commands.*;
import org.backblue.commands.Module;
import org.backblue.events.*;
import org.backblue.events.jobs.JobRunner;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

public class Core {

    public static final String VERSION = "0.5.4";
    public static JDA BOT;
    public static long BOOT = Instant.now().getEpochSecond();
    public static String SERVER_RULES;
    public static final Properties SECURE_KEYS = new Properties();
    public static JSONObject SETTINGS;
    public static JSONObject SAFETY;
    public static LinkedHashMap<String, Boolean> MODULES = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> MODULES_DESC = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> DEPLOYMENT = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> ANALYTICS = new LinkedHashMap<>();
    public static ContentSafetyClient CONTENT_SAFETY_CLIENT;

    private static void loadKeys() throws InvalidBotStateException {
        try {
            String rawKey = Files.readString(Path.of("data/keys.properties"));
            SECURE_KEYS.load(new java.io.StringReader(rawKey));
        } catch (Exception e) {
            throw new InvalidBotStateException("Required: '/data/keys.properties' cannot be loaded.");
        }
        try {
            Core.SERVER_RULES = Files.readString(Path.of("data/rules.txt"));
        } catch (Exception e) {
            throw new InvalidBotStateException("Required: '/data/rules.txt' cannot be loaded.");
        }
    }
    private static void loadSettings() throws InvalidBotStateException {
        try {
            String rawSettings = Files.readString(Path.of("data/settings.json"));
            Core.SETTINGS = new JSONObject(new JSONTokener(rawSettings));
        } catch (Exception e) {
            throw new InvalidBotStateException("Required: '/data/settings.json' cannot be loaded.");
        }
    }

    private static void loadSafety() throws InvalidBotStateException {
        try {
            String rawSafety = Files.readString(Path.of("data/safety.json"));
            Core.SAFETY = new JSONObject(new JSONTokener(rawSafety));
        } catch (Exception e) {
            throw new InvalidBotStateException("Required: '/data/safety.json' cannot be loaded.");
        }
    }

    public static void loadModules() throws InvalidBotStateException {
        loadSettings();
        try {
            JSONArray modulesArray = Core.SETTINGS.getJSONArray("modules");
            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject module = modulesArray.getJSONObject(i);
                Core.MODULES.put(module.getString("name"), module.getBoolean("enabled"));
                Core.MODULES_DESC.put(module.getString("name"), module.getString("desc"));
            }
        } catch (Exception e) {
            throw new InvalidBotStateException("Required: Modules cannot be loaded.");
        }
    }

    public static void loadAnalytics() throws InvalidBotStateException {
        try {
            JSONObject analytics = Core.SETTINGS.getJSONObject("analytics");
            for (String key : analytics.keySet()) {
                Core.ANALYTICS.put(key, analytics.getString(key));
            }
        } catch (Exception e) {
            throw new InvalidBotStateException("Required: Startup properties - Analytics cannot be loaded.");
        }
    }

    public static void loadDeployment() throws InvalidBotStateException {
        try {
            JSONObject deployment = Core.SETTINGS.getJSONObject("deployment");

            Core.DEPLOYMENT.put("guild", deployment.getString("guild"));
            Core.DEPLOYMENT.put("role.senior", deployment.getJSONObject("alerts").getString("all"));
            Core.DEPLOYMENT.put("role.mod", deployment.getJSONObject("alerts").getString("optIn"));
            Core.DEPLOYMENT.put("channel.cmd", deployment.getJSONObject("channels").getString("cmd"));
            Core.DEPLOYMENT.put("channel.log", deployment.getJSONObject("channels").getString("log"));
            Core.DEPLOYMENT.put("channel.warn", deployment.getJSONObject("channels").getString("warn"));

            JSONObject autoActions = deployment.getJSONObject("autoModActions");
            for (String key : autoActions.keySet()) {
                Core.DEPLOYMENT.put("action." + key, autoActions.getString(key));
            }

        } catch (Exception e) {
            throw new InvalidBotStateException("Required: Startup properties - Deployment cannot be loaded.");
        }
    }


    public static void main(String[] args) throws InvalidBotStateException {
        loadKeys();
        loadModules();
        loadDeployment();
        loadAnalytics();
        loadSafety();

        BOT = JDABuilder.create(Core.SECURE_KEYS.getProperty("TOKEN"), EnumSet.allOf(GatewayIntent.class))
                .setActivity(Activity.customStatus("Facilitating requests"))
                .setStatus(OnlineStatus.DO_NOT_DISTURB)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .build();

        // Register Events and Modules
        BOT.addEventListener(new PrivateMessage(), new EnforceOneOP(), new EnforceFanRole(), new AutoModAlert(), new EnforceSafetyFeatures(), new EnforceLinkChecks());

        // Register Commands
        BOT.addEventListener(new CommandManager(), new Ping(), new Module(), new Uptime(), new Data(), new Safety());

        // Azure Content Safety
        CONTENT_SAFETY_CLIENT = new ContentSafetyClientBuilder()
                .endpoint(Core.SECURE_KEYS.getProperty("AZURE_SAFETY_ENDPOINT"))
                .credential(new AzureKeyCredential(Core.SECURE_KEYS.getProperty("AZURE_SAFETY_KEY")))
                .buildClient();

        if (Core.SETTINGS.get("useSQL").equals(true)) {
            try {
                Connection test = DriverManager.getConnection(Core.SECURE_KEYS.getProperty("JDBC"));
                test.close();
            } catch (SQLException e) {
                System.out.println("Failed to connect to server. Turn off 'useSQL' in settings.json or check your JDBC URL.\n" + e);
                System.exit(1);
            }
        }

        JobRunner jobRunner = new JobRunner();
        Thread jobThread = new Thread(jobRunner);
        jobThread.start();
    }

}