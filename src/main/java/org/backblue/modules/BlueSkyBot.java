package org.backblue.modules;

import net.dv8tion.jda.api.EmbedBuilder;
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
    private final String footer;
    private final String footerIcon;
    private final HashMap<String, String> bSkyMap = new HashMap<>();
    private final HashMap<String, Instant> bSkyUserLastPost = new HashMap<>();

    public BlueSkyBot(String user, String pass, JSONObject json, String footer, String footerIcon) {
        if (user == null || pass == null || json == null || footer == null || footerIcon == null) {
            System.err.println("BlueSkyBot: No BlueSky credentials or users configured!");
            this.user = null;
            this.pass = null;
            this.footer = null;
            this.footerIcon = null;
            return;
        }
        this.user = user;
        this.pass = pass;
        this.footer = footer;
        this.footerIcon = footerIcon;
        for (String key : json.keySet()) {
            bSkyMap.put(key, json.getString(key));
            bSkyUserLastPost.put(key, Instant.now());
            System.out.println("BlueSkyBot: Monitoring user " + key + " and posting to channel ID " + json.getString(key));
            scheduler().scheduleWithFixedDelay(() -> {
                try {checkAccount(key);} catch (Exception ignored) {}}, 1, 1, TimeUnit.MINUTES);
        }
    }

    private void checkAccount(String did) {
        try {
            JSONObject post = getUserFeed(did);
            if (post == null) {
                System.out.println("BlueSkyBot: No posts found for user " + did);
                return;
            }
            Instant postTime = Instant.parse(post.getJSONObject("record").getString("createdAt"));
            if (postTime.getEpochSecond() < bSkyUserLastPost.get(did).getEpochSecond()) {
                System.out.println("BlueSkyBot: No new posts found for user " + did);
                return;
            }
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.CYAN);
            StringBuilder desc = new StringBuilder(post.getJSONObject("record").getString("text"));

        } catch (IOException | InterruptedException e) {
            System.err.println("BlueSkyBot: Can not fetch feed for user " + did + "\n" + e);
        }
    }

    private JSONObject getUserFeed(String did) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://bsky.social/xrpc/com.atproto.server.createSession"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new JSONObject().put("identifier", user).put("password", pass).toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject responseJson = new JSONObject(response.body());
        String accessJwt = responseJson.getString("accessJwt");
        String endpoint = "https://bsky.social/xrpc/app.bsky.feed.getAuthorFeed?actor=" + did;
        HttpRequest requestHttp = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessJwt)
                .GET()
                .build();

        HttpResponse<String> responseHttp = client.send(requestHttp, HttpResponse.BodyHandlers.ofString());
        JSONObject data = new JSONObject(responseHttp.body());

        JSONArray feed = data.getJSONArray("feed");
        if (feed.isEmpty()) {
            return null;
        }
        return feed.getJSONObject(0).getJSONObject("post");
    }

    @Override
    public String name() {
        return "bSkyTracker";
    }
}
