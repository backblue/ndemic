package org.backblue.libraries;

import net.dv8tion.jda.api.entities.User;
import org.backblue.Core;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public class UserJSON {

    private final String userId;
    private final JSONObject json;
    public static boolean userFile(String userId) {
        return Files.exists(Path.of("data/users/" + userId));
    }
    public static JSONObject readUserJSON(String userId) {
        try {
            return new JSONObject(Files.readString(Path.of("data/users/" + userId)));
        } catch (Exception e) {
            return new JSONObject().put("id", userId).put("lastRefresh", Instant.now()).put("cachedUsername", Objects.requireNonNull(Core.BOT.getUserById(userId)).getName());
        }
    }

    public void write() {
        try {
            System.out.println(json);
            json.remove("lastRefresh");
            json.remove("cachedUsername");
            json.put("lastRefresh", Instant.now());
            json.put("cachedUsername", Objects.requireNonNull(Core.BOT.getUserById(userId)).getName());
            Files.writeString(Path.of("data/users/" + userId + ".json"), json.toString());
        } catch (Exception e) {
            System.out.println(Instant.now() + " - Error writing user file: " + e.getMessage());
        }
    }
    public UserJSON writeString(String key, String value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public UserJSON writeInt(String key, int value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public UserJSON writeLong(String key, Long value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public UserJSON writeJSONObject(String key, JSONObject value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public UserJSON writeJSONArray(String key, JSONArray value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public static UserJSON get(String userId) {
        return new UserJSON(userId);
    }

    private UserJSON(String userId) {
        this.userId = userId;
        this.json = readUserJSON(userId);
    }

}
