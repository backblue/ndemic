package org.backblue.modules;

import org.backblue.utilities.NdemicModule;
import org.json.JSONObject;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class BlueSkyBot implements NdemicModule {

    private final String user;
    private final String pass;
    private final HashMap<String, String> bSkyMap = new HashMap<>();
    private final HashMap<String, Instant> bSkyUserLastPost = new HashMap<>();

    public BlueSkyBot(String user, String pass, JSONObject json) {
        if (user == null || pass == null || json == null) {
            System.err.println("BlueSkyBot: No BlueSky credentials or users configured!");
        }
        this.user = user;
        this.pass = pass;
        for (String key : json.keySet()) {
            bSkyMap.put(key, json.getString(key));
            bSkyUserLastPost.put(key, Instant.EPOCH);
            System.out.println("BlueSkyBot: Monitoring user " + key + " and posting to channel ID " + json.getString(key));
            scheduler().scheduleWithFixedDelay(() -> {
                try {checkUser(key);} catch (Exception ignored) {}}, 1, 1, TimeUnit.MINUTES);
        }
    }

    private void checkUser(String did) {

    }

    @Override
    public String name() {
        return "bSkyTracker";
    }
}
