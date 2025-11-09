package org.backblue.wrappers;

import net.dv8tion.jda.api.EmbedBuilder;
import org.backblue.Bot;
import org.backblue.utilities.NdemicModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class RedditBot implements NdemicModule {

    public final HashMap<String, String> subredditMap = new HashMap<>();
    private final HashMap<String, String> subredditLastPost = new HashMap<>();

    @Override
    public String name() {
        return "reddit";
    }

    public RedditBot(JSONObject subreddits) {
        if (subreddits == null) {
            System.err.println("RedditBot: No subreddits configured, disabling Reddit module.");
            return;
        }
        long cd = 1;
        for (String key : subreddits.keySet()) {
            if (key.charAt(0) == '_') {
                if (key.contains("cooldown")) {
                    cd = subreddits.getLong(key);
                    continue;
                }
            }
            JSONArray data = fetchData(key, true);
            if (data == null) {
                continue;
            }
            String lastPostID = data.getJSONObject(0).getJSONObject("data").getString("id");
            subredditMap.put(key, subreddits.getString(key));
            subredditLastPost.put(key, lastPostID);
            System.out.println("RedditBot: Monitoring subreddit r/" + key + " and posting to channel ID " + subreddits.getString(key));
            scheduler().scheduleWithFixedDelay(() -> {
                try {checkSubreddit(key);} catch (Exception ignored) {}}, 1, cd, TimeUnit.MINUTES);
        }
    }

    private void checkSubreddit(String subreddit) {
        System.out.println("RedditChecker: Looking up data for subreddit r/" + subreddit);
        JSONArray jsonArray = fetchData(subreddit, false);
        int i = 0;
        String firstNewPostID = null;
        if (jsonArray == null) {
            System.out.println("RedditChecker: didn't find anything for " + subreddit + " :(");
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
        for (; i > 0 && i < 10; i--) {
            System.out.println("RedditChecker: found " + i + " new posts since last check for r/" + subreddit);
            JSONObject a = jsonArray.getJSONObject(i - 1).getJSONObject("data");

            EmbedBuilder e = new EmbedBuilder();
            e.setTitle(a.getString("title"));
            e.setDescription(a.getString("selftext"));
            e.setUrl("https://reddit.com" + a.getString("permalink"));
            e.setFooter("u/" + a.getString("author"));
            e.setTimestamp(Instant.ofEpochSecond(a.getLong("created_utc")));
            e.setColor(Color.CYAN);
            if (a.has("preview")) {
                String link = a.getJSONObject("preview").getJSONArray("images").getJSONObject(0).getJSONObject("source").getString("url");
                e.setImage(link.replace("&amp;", "&"));
            }
            e.setAuthor("New post in r/" + subreddit,
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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.79 Safari/537.36")
                .GET()
                .build();
        try {
            HttpClient client = HttpClient.newHttpClient();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                return jsonResponse.getJSONObject("data").getJSONArray("children");
            } else {
                System.err.println("Unknown response code " + response.statusCode());
                FileWriter writer = new FileWriter("error.txt");
                writer.write("Error fetching subreddit " + subreddit + ": " + response.statusCode() + "\n" + response.body());
                writer.close();
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

