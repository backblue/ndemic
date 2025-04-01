package org.backblue.libraries;

import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

public class UserData {

    public static boolean userFile(String userId) {
        return Files.exists(Path.of("data/users/" + userId));
    }

    public static JSONObject getUserJSON(String userId) {
        try {
            return new JSONObject(Files.readString(Path.of("data/users/" + userId)));
        } catch (Exception e) {
            return null;
        }
    }

    public static void writeStringData(String userId, JSONObject object, String key, String value) {

    }
     private UserData() {}

}
