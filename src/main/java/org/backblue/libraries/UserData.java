package org.backblue.libraries;

import org.backblue.Core;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class UserData {

    public static boolean userFile(String userId) {
        return Files.exists(Path.of("data/users/" + userId));
    }

    public static JSONObject readUserJSON(String userId) {
        try {
            return new JSONObject(Files.readString(Path.of("data/users/" + userId)));
        } catch (Exception e) {
            return new JSONObject().put("id", userId).put("lastRefresh", Instant.now()).put("cachedUsername", Core.BOT.getUserById(userId).getName());
        }
    }

    public static JSONObject writeUserJSON(String userId, JSONObject json) {
        try {
            json.remove("lastRefresh");
            json.remove("cachedUsername");
            json.put("lastRefresh", Instant.now());
            json.put("cachedUsername", Core.BOT.getUserById(userId).getName());
            Files.writeString(Path.of("data/users/" + userId + ".json"), json.toString());
            return json;
        } catch (Exception e) {
            System.out.println(Instant.now() + " - Error writing user file: " + e.getMessage());
            return null;
        }
    }
    public static JSONObject writeJsonString(JSONObject json, String key, String value) {
        if (json.has(key)) {
            json.remove(key);
        }
        return json.put(key, value);
    }

    public static JSONObject writeJsonInt(JSONObject json, String key, int value) {
        if (json.has(key)) {
            json.remove(key);
        }
        return json.put(key, value);
    }

    public static JSONObject writeJsonLong(JSONObject json, String key, Long value) {
        if (json.has(key)) {
            json.remove(key);
        }
        return json.put(key, value);
    }

    private UserData() {}

}
