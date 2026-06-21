package org.backblue.utilities;

import org.backblue.core.Bot;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public final class BlueSky {

    private final Bot bot;
    private final String user;
    private final String pass;
    private final HashMap<String, String> bSkyMap = new HashMap<>();
    private final HashMap<String, Instant> bSkyUserLastPost = new HashMap<>();
    private String accessJwt = null;
    private Instant tokenExpiry = Instant.EPOCH;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Logger Log = LoggerFactory.getLogger(BlueSky.class);

    public BlueSky(String user, String pass, JSONObject json, Bot bot) {
        this.bot = bot;
        if (user == null || pass == null || json == null ) {
            this.user = null;
            this.pass = null;
            Log.warn("No BlueSky credentials or users configured");
            bot.disableFeature(FeatureFlag.BlueSky);
            return;
        }
        this.user = user;
        this.pass = pass;
        for (String key : json.keySet()) {
            if (!key.equals("onlyLink") && json.get(key) instanceof String) {
                bSkyMap.put(key, json.getString(key));
                bSkyUserLastPost.put(key, Instant.now());
                Log.info("Monitoring {} and posting to {}", key, json.get(key));
                bot.getScheduler().scheduleWithFixedDelay(() -> {
                    try {
                        checkAccount(key);
                    } catch (Exception ignored) {
                    }
                }, 1, 1, TimeUnit.MINUTES);
            }
        }
    }

    private void checkAccount(String did) {
        if (bot.isFeatureEnabled(FeatureFlag.BlueSky)) {
            return;
        }
        try {
            JSONObject post = getUserFeed(did);
            if (post == null) {
                return;
            }
            Instant postTime = Instant.parse(post.getJSONObject("record").getString("createdAt"));
            if (!postTime.isAfter(bSkyUserLastPost.get(did))) {
                return;
            }
            Log.info("New post found for: {}", did);
            String[] parts = post.getString("uri").split("/");
            String rKey = parts[parts.length - 1];
            String urlInTxt = "https://bsky.app/profile/" + post.getJSONObject("author").getString("handle") + "/post/" + rKey;
            bot.getIO().send(bSkyMap.get(did), urlInTxt);
            bSkyUserLastPost.put(did, postTime);
        } catch (IOException | InterruptedException e) {
            Log.warn("Can not fetch feed for user {}: {}", did, e.getMessage());
        }
    }

    private JSONObject getUserFeed(String did) throws IOException, InterruptedException {
        login();

        String endpoint = "https://bsky.social/xrpc/app.bsky.feed.getAuthorFeed?actor=" + did;

        HttpRequest feedRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessJwt)
                .GET()
                .build();

        HttpResponse<String> feedResponse = HTTP_CLIENT.send(
                feedRequest,
                HttpResponse.BodyHandlers.ofString()
        );

        JSONObject data = new JSONObject(feedResponse.body());
        JSONArray feed = data.getJSONArray("feed");
        for (int i = 0; i < feed.length(); i++) {
            JSONObject post = feed.getJSONObject(i).getJSONObject("post");
            JSONObject record = post.getJSONObject("record");
            if (!record.has("reply")) {
                return post;
            }
        }
        return null;
    }

    private synchronized void login() throws IOException, InterruptedException {
        if (accessJwt != null && Instant.now().isBefore(tokenExpiry)) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://bsky.social/xrpc/com.atproto.server.createSession"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        new JSONObject()
                                .put("identifier", user)
                                .put("password", pass)
                                .toString()
                ))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject json = new JSONObject(response.body());
        accessJwt = json.getString("accessJwt");

        tokenExpiry = Instant.now().plusSeconds(60 * 60 * 2 - 60);
    }

}