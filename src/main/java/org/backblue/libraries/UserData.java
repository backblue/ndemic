package org.backblue.libraries;

import org.backblue.Core;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
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

    public static int writeUserToFile(String userId, JSONObject json) {
        if (!userFile(userId)) {
            json.put("id", userId);
            json.put("lastKnownUsername", Core.BOT.getUserById(userId).getName());
            try {
                Files.createFile(Path.of("data/users/" + userId));
            } catch (Exception e) {}
        }
        if (userFile(userId)) {
            json.remove("lastKnownUsername");
            json.put("lastKnownUsername", Core.BOT.getUserById(userId).getName());
            try {
                FileWriter write = new FileWriter("data/users/" + userId);
                write.write(json.toString());
                write.close();
            } catch (IOException e) {
                System.out.println("user write fail (writer fail)");
                return -1;
            }
            return 0;
        } else {
            System.out.println("user write fail (no file exist)");
            return -1;
        }
     }

     private UserData() {}

}
