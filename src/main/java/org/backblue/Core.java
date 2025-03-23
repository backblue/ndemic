package org.backblue;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.backblue.commands.CommandManager;
import org.backblue.commands.Module;
import org.backblue.commands.Ping;
import org.backblue.commands.Uptime;
import org.backblue.events.AutoModAlert;
import org.backblue.events.EnforceFanRole;
import org.backblue.events.EnforceOneOP;
import org.backblue.events.PrivateMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;

public class Core {

    public static long BOOT = Instant.now().getEpochSecond();
    public static String SERVER_RULES;
    private static String LOGIN_TOKEN;
    public static JSONObject SETTINGS;
    public static LinkedHashMap<String, Boolean> MODULES = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> MODULES_DESC = new LinkedHashMap<>();

    private static void loadRules() {
        try {
            Core.SERVER_RULES = Files.readString(Path.of("data/rules.txt"));
        } catch (Exception e) {
            System.out.println("Error loading rules file. Bot stopped.");
            System.exit(1);
        }
    }

    private static void loadKey() {
        try {
            String rawKey = Files.readString(Path.of("data/key.json"));
            JSONObject key = new JSONObject(new JSONTokener(rawKey));
            Core.LOGIN_TOKEN = key.getString("token");
        } catch (Exception e) {
            System.out.println("Error loading key file. Bot stopped.");
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

    public static void main(String[] args) {
        loadKey();
        loadModules();
        loadRules();

        JDA bot = JDABuilder.create(Core.LOGIN_TOKEN, EnumSet.allOf(GatewayIntent.class))
                .setActivity(Activity.customStatus("Facilitating requests"))
                .setStatus(OnlineStatus.DO_NOT_DISTURB)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .build();

        // Register Events and Modules
        bot.addEventListener(new PrivateMessage(), new AutoModAlert(), new EnforceOneOP(), new EnforceFanRole());

        // Register Commands
        bot.addEventListener(new CommandManager(), new Ping(), new Module(), new Uptime());
    }
}