package org.backblue.utilities;

import net.dv8tion.jda.api.EmbedBuilder;
import org.backblue.Bot;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RedditChecker implements NdemicModule {

    private final HashMap<String, String> subredditMap = new HashMap<>();
    private final HashMap<String, String> subredditLastPost = new HashMap<>();

    public boolean isEnabled() {
        return Bot.getBot().getModuleValue("reddit");
    }

    public ScheduledExecutorService scheduler() {
        return Bot.getBot().getScheduler();
    }

    public RedditChecker(JSONObject subreddits) {
        for (String key : subreddits.keySet()) {
            JSONArray data = fetchData(key, true);
            if (data == null) {
                continue;
            }
            String lastPostID = data.getJSONObject(0).getJSONObject("data").getString("id");
            subredditMap.put(key, subreddits.getString(key));
            subredditLastPost.put(key, lastPostID);
            System.out.println("RedditChecker: Monitoring subreddit r/" + key + " and posting to channel ID " + subreddits.getString(key));
            scheduler().scheduleWithFixedDelay(() -> {
                try {checkSubreddit(key);} catch (Exception ignored) {}}, 1, 1, TimeUnit.MINUTES);
        }
    }

    private void checkSubreddit(String subreddit) {
        JSONArray jsonArray = fetchData(subreddit, false);
        System.out.println("RedditChecker: Looking up data for subreddit r/" + subreddit);
        int i = 0;
        String firstNewPostID = null;
        if (jsonArray == null) {
            return;
        }
        for (; i < jsonArray.length(); i++) {
            JSONObject a = jsonArray.getJSONObject(i).getJSONObject("data");
            if (i == 0) {
                firstNewPostID = a.getString("id");
            }
            if (a.getString("id").equals(subredditLastPost.get(subreddit))) {
                break;
            }
        }
        subredditLastPost.put(subreddit, firstNewPostID);
        if (i == 0) {
            System.out.println("RedditChecker: found " + i + " new posts since last check for r/" + subreddit);
        }
        for (; i > 0; i--) {
            System.out.println("RedditChecker: found " + i + " new posts since last check for r/" + subreddit);

            String postPrefix = "";

            JSONObject a = jsonArray.getJSONObject(i - 1).getJSONObject("data");

            EmbedBuilder e = new EmbedBuilder();
            e.setTitle(a.getString("title"));
            e.setDescription(a.getString("selftext"));
            e.setUrl("https://reddit.com" + a.getString("permalink"));
            e.setFooter("u/" + a.getString("author"));
            e.setTimestamp(Instant.ofEpochSecond(a.getLong("created_utc")));
            e.setColor(Color.CYAN);
            if (a.has("preview")) {
                e.setImage(a.getString("url_overridden_by_dest"));
                postPrefix = " image";
            } else if (a.has("crosspost_parent_list")) {
                postPrefix = " link";
            }
            e.setAuthor("New" + postPrefix + " post in r/" + subreddit,
                    "https://reddit.com" + a.getString("permalink"),
                    getIcon(subreddit));
            System.out.println("RedditChecker: Sending new post embed for r/" + subreddit + " to channel ID " + subredditMap.get(subreddit));
            Bot.getBot().sendTextChannelMessage(subredditMap.get(subreddit), e.build());
        }
    }

    private JSONArray fetchData(String subreddit, boolean firstTime) {
        if (!isEnabled()) {
            if (!firstTime) {
                return null;
            }
        }
        String url = "https://www.reddit.com/r/" + subreddit + "/new.json";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                return jsonResponse.getJSONObject("data").getJSONArray("children");
            }
        } catch (InterruptedException | IOException e) {
            System.err.println("Error fetching data for subreddit: " + subreddit);
            e.printStackTrace();
        }
        return null;
    }

    private String getIcon(String subreddit) {
        try {
            String url = "https://www.reddit.com/r/" + subreddit + "/about.json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            var client = java.net.http.HttpClient.newHttpClient();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                String link = jsonResponse.getJSONObject("data").getString("community_icon");
                int cutoffPNG = link.indexOf(".png");
                int cutoffJPG = link.indexOf(".jpg");
                int cutoff = Math.max(cutoffPNG, cutoffJPG);
                return link.substring(0, cutoff + 4);
            }
        } catch (IOException | InterruptedException e) {
            return "";
        }
        return "";
    }

}
