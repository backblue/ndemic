package org.backblue.utilities;

import org.backblue.core.Bot;
import org.backblue.enums.Feature;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public final class BlueSky {

    private final Bot bot;
    private final HashMap<String, String> bSkyMap = new HashMap<>();
    private final HashMap<String, Instant> bSkyUserLastPost = new HashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Logger Log = LoggerFactory.getLogger(BlueSky.class);

    public BlueSky(String user, String pass, JSONObject json, Bot bot) {
        this.bot = bot;
        // Public author feeds do not require a session or credentials.
        if (json == null) {
            Log.warn("No BlueSky users configured");
            bot.disableFeature(Feature.BlueSky);
            return;
        }
        for (String key : json.keySet()) {
            if (!key.equals("onlyLink") && json.get(key) instanceof String) {
                bSkyMap.put(key, json.getString(key));
                bSkyUserLastPost.put(key, Instant.now());
                Log.info("Monitoring '{}' and posting to channel #{}", key, json.get(key));
                bot.getScheduler().scheduleWithFixedDelay(() -> {
                    try {
                        checkAccount(key);
                    } catch (Exception e) {
                        Log.error("Failed to check account", e);
                    }
                }, 1, 1, TimeUnit.MINUTES);
            }
        }
    }

    private void checkAccount(String did) {
        if (!bot.isFeatureEnabled(Feature.BlueSky)) {
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("Interrupted fetching feed for user {}", did);
        } catch (IOException e) {
            Log.warn("Can not fetch feed for user {}: {}", did, e.getMessage());
        }
    }

    private JSONObject getUserFeed(String did) throws IOException, InterruptedException {
        String endpoint = "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed?actor="
                + URLEncoder.encode(did, StandardCharsets.UTF_8)
                + "&filter=posts_no_replies&limit=100";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return parseFeedResponse(response.statusCode(), response.body());
    }

    static JSONObject parseFeedResponse(int statusCode, String body) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            // Do not log response bodies: they can contain sensitive or untrusted data.
            throw new IOException("Bluesky author feed returned HTTP " + statusCode);
        }
        try {
            JSONArray feed = new JSONObject(body).getJSONArray("feed");
            JSONObject newest = null;
            Instant newestTime = Instant.MIN;
            for (int i = 0; i < feed.length(); i++) {
                JSONObject entry = feed.getJSONObject(i);
                JSONObject post = entry.getJSONObject("post");
                JSONObject record = post.getJSONObject("record");
                // Reposts belong to another author and must not mask this author's posts.
                JSONObject reason = entry.optJSONObject("reason");
                boolean repost = reason != null && "app.bsky.feed.defs#reasonRepost"
                        .equals(reason.optString("$type"));
                if (record.has("reply") || repost) {
                    continue;
                }
                Instant createdAt = Instant.parse(record.getString("createdAt"));
                if (createdAt.isAfter(newestTime)) {
                    newest = post;
                    newestTime = createdAt;
                }
            }
            return newest;
        } catch (JSONException | java.time.format.DateTimeParseException e) {
            throw new IOException("Invalid Bluesky author feed response", e);
        }
    }
}
