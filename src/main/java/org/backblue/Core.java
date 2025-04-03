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
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.backblue.commands.CommandManager;
import org.backblue.commands.Module;
import org.backblue.commands.Ping;
import org.backblue.commands.Safety;
import org.backblue.commands.Uptime;
import org.backblue.events.AutoModAlert;
import org.backblue.events.EnforceFanRole;
import org.backblue.events.EnforceOneOP;
import org.backblue.events.PrivateMessage;
import org.backblue.libraries.Job;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class Core {

    public static JDA BOT;
    public static long BOOT = Instant.now().getEpochSecond();
    public static String SERVER_RULES;
    private static Properties SECURE_KEYS = new Properties();
    public static JSONObject SETTINGS;
    public static JSONObject SAFETY;
    public static LinkedHashMap<String, Boolean> MODULES = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> MODULES_DESC = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> DEPLOYMENT = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> ANALYTICS = new LinkedHashMap<>();
    public static ContentSafetyClient CONTENT_SAFETY_CLIENT;

    private static void loadKeys() {
        try {
            String rawKey = Files.readString(Path.of("data/keys.properties"));
            SECURE_KEYS.load(new java.io.StringReader(rawKey));
        } catch (Exception e) {
            System.out.println("Error loading key file. Bot stopped.");
            System.exit(1);
        }
        try {
            Core.SERVER_RULES = Files.readString(Path.of("data/rules.txt"));
        } catch (Exception e) {
            System.out.println("Error loading rules file. Bot stopped.");
            System.exit(1);
        }
    }
    private static void loadSettings() {
        try {
            String rawSettings = Files.readString(Path.of("data/settings.json"));
            Core.SETTINGS = new JSONObject(new JSONTokener(rawSettings));
        } catch (Exception e) {
            System.out.println("Error loading settings file. Bot stopped.");
            System.exit(1);
        }
    }

    private static void loadSafety() {
        try {
            String rawSafety = Files.readString(Path.of("data/safety.json"));
            Core.SAFETY = new JSONObject(new JSONTokener(rawSafety));
        } catch (Exception e) {
            System.out.println("Error loading safety file. Bot stopped.");
            System.exit(1);
        }
    }

    public static void loadModules() {
        loadSettings();
        try {
            JSONArray modulesArray = Core.SETTINGS.getJSONArray("modules");
            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject module = modulesArray.getJSONObject(i);
                Core.MODULES.put(module.getString("name"), module.getBoolean("enabled"));
                Core.MODULES_DESC.put(module.getString("name"), module.getString("desc"));
            }
        } catch (Exception e) {
            System.out.println("Error loading modules. Bot stopped.");
            System.exit(1);
        }
    }

    public static void loadAnalytics() {
        try {
            JSONObject analytics = Core.SETTINGS.getJSONObject("analytics");
            for (String key : analytics.keySet()) {
                Core.ANALYTICS.put(key, analytics.getString(key));
            }
        } catch (Exception e) {
            System.out.println("Error loading analytics. Bot stopped.");
            System.exit(1);
        }
    }

    public static void loadDeployment() {
        try {
            JSONObject deployment = Core.SETTINGS.getJSONObject("deployment");

            Core.DEPLOYMENT.put("guild", deployment.getString("guild"));
            Core.DEPLOYMENT.put("role.senior", deployment.getJSONObject("roles").getString("senior"));
            Core.DEPLOYMENT.put("role.mod", deployment.getJSONObject("roles").getString("mod"));
            Core.DEPLOYMENT.put("channel.cmd", deployment.getJSONObject("channels").getString("cmd"));
            Core.DEPLOYMENT.put("channel.log", deployment.getJSONObject("channels").getString("log"));
            Core.DEPLOYMENT.put("channel.warn", deployment.getJSONObject("channels").getString("warn"));

            Core.DEPLOYMENT.put("kick.length", String.valueOf(deployment.getJSONArray("autoModKick").length()));
            for (int i = 0; i < deployment.getJSONArray("autoModKick").length(); i++) {
                Core.DEPLOYMENT.put("kick." + i, deployment.getJSONArray("autoModKick").getString(i));
            }
            Core.DEPLOYMENT.put("ban.length", String.valueOf(deployment.getJSONArray("autoModBan").length()));
            for (int i = 0; i < deployment.getJSONArray("autoModBan").length(); i++) {
                Core.DEPLOYMENT.put("ban." + i, deployment.getJSONArray("autoModBan").getString(i));
            }

        } catch (Exception e) {
            System.out.println("Error loading deployments. Bot stopped.");
            System.exit(1);
        }
    }


    public static void main(String[] args) {
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
        BOT.addEventListener(new PrivateMessage(), new EnforceOneOP(), new EnforceFanRole(), new AutoModAlert(), new Safety());

        // Register Commands
        BOT.addEventListener(new CommandManager(), new Ping(), new Module(), new Uptime());

        // Azure Content Safety
        CONTENT_SAFETY_CLIENT = new ContentSafetyClientBuilder()
                .endpoint(Core.SECURE_KEYS.getProperty("AZURE_SAFETY_ENDPOINT"))
                .credential(new AzureKeyCredential(Core.SECURE_KEYS.getProperty("AZURE_SAFETY_KEY")))
                .buildClient();

        Timer task = new Timer();
        TimerTask tasks = new TimerTask() {
            @Override
            public void run() {
                if (Core.MODULES.get("safetyFeatures")) {
                    Job.process();
                }
            }
        };
        task.schedule(tasks, 0, Core.SAFETY.getInt("jobFrequency")*1000L);
    }
}