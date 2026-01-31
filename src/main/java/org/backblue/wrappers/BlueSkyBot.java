package org.backblue.wrappers;

import org.backblue.Bot;
import org.backblue.utilities.NdemicModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class BlueSkyBot implements NdemicModule {

    private final String user;
    private final String pass;
    private final HashMap<String, String> bSkyMap = new HashMap<>();
    private final HashMap<String, Instant> bSkyUserLastPost = new HashMap<>();
    private String accessJwt = null;
    private Instant tokenExpiry = Instant.EPOCH;

    public BlueSkyBot(String user, String pass, JSONObject json, String footer, String footerIcon) {
        if (user == null || pass == null || json == null || footer == null || footerIcon == null) {
            System.err.println("BlueSkyBot: No BlueSky credentials or users configured!");
            this.user = null;
            this.pass = null;
            return;
        }
        this.user = user;
        this.pass = pass;
        for (String key : json.keySet()) {
            if (!key.equals("onlyLink") && json.get(key) instanceof String) {
                bSkyMap.put(key, json.getString(key));
                bSkyUserLastPost.put(key, Instant.now());
                System.out.println("BlueSkyBot: Monitoring user " + key + " and posting to channel ID " + json.getString(key));
                scheduler().scheduleWithFixedDelay(() -> {
                    try {
                        checkAccount(key);
                    } catch (Exception ignored) {
                    }
                }, 1, 1, TimeUnit.MINUTES);
            }
        }
    }

    private void checkAccount(String did) {
        if (!isEnabled()) {
            return;
        }
        try {
            JSONObject post = getUserFeed(did);
            if (post == null) {
                return;
            }
            Instant postTime = Instant.parse(post.getJSONObject("record").getString("createdAt"));
            if (!postTime.isAfter(bSkyUserLastPost.get(did))) {
                System.out.println("BlueSkyBot: No new posts found for user " + did);
                return;
            }
            System.out.println("BlueSkyBot: New post discovered for: " + did);
            boolean success;
            String[] parts = post.getString("uri").split("/");
            String rKey = parts[parts.length - 1];
            String urlInTxt = "https://bsky.app/profile/" + post.getJSONObject("author").getString("handle") + "/post/" + rKey;
            success = Bot.getBot().sendTextChannelMessage(bSkyMap.get(did), urlInTxt);
            if (success) {
                bSkyUserLastPost.put(did, postTime);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("BlueSkyBot: Can not fetch feed for user " + did + "\n" + e);
        }
    }

    private JSONObject getUserFeed(String did) throws IOException, InterruptedException {
        login();

        String endpoint =
                "https://bsky.social/xrpc/app.bsky.feed.getAuthorFeed?actor=" + did;

        HttpRequest feedRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessJwt)
                .GET()
                .build();

        HttpResponse<String> feedResponse =
                HttpClient.newHttpClient().send(
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

        HttpClient client = HttpClient.newHttpClient();
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

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject json = new JSONObject(response.body());
        accessJwt = json.getString("accessJwt");

        tokenExpiry = Instant.now().plusSeconds(60 * 60 * 2 - 60);
    }



    @Override
    public String name() {
        return "bSkyTracker";
    }
}
