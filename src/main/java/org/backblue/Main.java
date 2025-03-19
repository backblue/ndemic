package org.backblue;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;

public class Main {

    public static JSONObject settings = new JSONObject();
    public static HashMap<String, Boolean> modules = new HashMap<>();


    public static void main(String[] args) {

        try {
            String rawKey = Files.readString(Path.of("data/key.json"));
            JSONObject key = new JSONObject(new JSONTokener(rawKey));

            String rawSettings = Files.readString(Path.of("data/settings.json"));
            JSONObject settings = new JSONObject(new JSONTokener(rawSettings));
            JSONArray modulesArray = settings.getJSONArray("modules");
            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject module = modulesArray.getJSONObject(i);
                String name = module.getString("name");
                boolean enabled = module.getBoolean("enabled");
                Main.modules.put(name, enabled);
            }

            JDA bot = JDABuilder.createLight(key.getString("token"), EnumSet.allOf(GatewayIntent.class))
                    .setActivity(Activity.customStatus("Facilitating requests"))
                    .setStatus(OnlineStatus.DO_NOT_DISTURB)
                    .build();
        } catch (Exception e) {
            System.out.println("Error reading config files");
        }

    }
}