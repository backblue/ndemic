package org.backblue.wrappers;

import net.dv8tion.jda.api.EmbedBuilder;
import org.backblue.Bot;
import org.backblue.utilities.NdemicModule;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class BlueSkyBot implements NdemicModule {

    private final String user;
    private final String pass;
    private final String footer;
    private final String footerIcon;
    private final HashMap<String, String> bSkyMap = new HashMap<>();
    private final HashMap<String, Instant> bSkyUserLastPost = new HashMap<>();
    private boolean linkOnly;

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
        this.linkOnly = json.keySet().contains("onlyLink");
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
            if (postTime.getEpochSecond() < bSkyUserLastPost.get(did).getEpochSecond()) {
                System.out.println("BlueSkyBot: No new posts found for user " + did);
                return;
            }
            System.out.println("BlueSkyBot: New post discovered for: " + did);
            boolean success;
            if (linkOnly) {
                String[] parts = post.getString("uri").split("/");
                String rKey = parts[parts.length - 1];
                String urlInTxt = "https://bsky.app/profile/" + post.getJSONObject("author").getString("handle") + "/post/" + rKey;
                success = Bot.getBot().sendTextChannelMessage(bSkyMap.get(did), urlInTxt);
            } else {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(Color.CYAN);
                StringBuilder desc = new StringBuilder(post.getJSONObject("record").getString("text"));
                JSONObject facets = post.getJSONObject("record").optJSONObject("facets");
                embed.setDescription(formatDescription(desc, facets));
                embed.setFooter(footer, footerIcon);

                embed.setTimestamp(postTime);
                embed.setAuthor(post.getJSONObject("author").getString("displayName"),
                        "https://bsky.app/profile/" + post.getJSONObject("author").getString("handle"),
                        post.getJSONObject("author").getString("avatar"));
                boolean imagefound = false;
                try {
                    embed.setImage(post.getJSONObject("embed").getJSONObject("external").getString("thumb"));
                    imagefound = true;
                } catch (JSONException ignored) {}
                if (!imagefound) {
                    try {
                        embed.setImage(post.getJSONObject("embed").getJSONObject("images").getJSONArray("images").getJSONObject(0).getString("fullsize"));
                    } catch (JSONException ignored) {}
                }

                String[] parts = post.getString("uri").split("/");
                String rKey = parts[parts.length - 1];
                String urlInTxt = "https://bsky.app/profile/" + post.getJSONObject("author").getString("handle") + "/post/" + rKey;
                success = Bot.getBot().sendTextChannelMessage(bSkyMap.get(did), urlInTxt, embed.build());
            }
            if (success) {
                bSkyUserLastPost.put(did, postTime);
            }
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
        return feed.getJSONObject(1).getJSONObject("post");
    }

    private String formatDescription(StringBuilder desc, JSONObject json) {
        if (json == null) {
            return desc.toString();
        }
        JSONArray facets = null;
        try {
            facets = json.getJSONArray("facets");
        } catch (JSONException ignored) {}
        int shiftedDesc = 0;
        for (int i = 0; facets != null && i < facets.length(); i++) {
            JSONObject facet = facets.getJSONObject(i);
            String type = facet.getJSONArray("features").getJSONObject(0).getString("$type");
            int start = facet.getJSONObject("index").getInt("byteStart") + shiftedDesc;
            int end = facet.getJSONObject("index").getInt("byteEnd") + shiftedDesc;
            if (type.equals("app.bsky.richtext.facet#link")) {
                byte[] descInBytes = desc.toString().getBytes();

                byte[] descByteToLink = Arrays.copyOfRange(descInBytes, 0, start);
                int sizeofDescByteToLink = descByteToLink.length + 1;
                byte[] sizeofDescByteToLinkWithBracket = new byte[sizeofDescByteToLink];
                System.arraycopy(descByteToLink, 0, sizeofDescByteToLinkWithBracket, 0, sizeofDescByteToLink - 1);
                sizeofDescByteToLinkWithBracket[sizeofDescByteToLink - 1] = (byte) '[';
                byte[] sizeofDescByteToLinkWithLeftBracketAndContent = new byte[sizeofDescByteToLink + (end - start) + 2];
                System.arraycopy(sizeofDescByteToLinkWithBracket, 0, sizeofDescByteToLinkWithLeftBracketAndContent, 0, sizeofDescByteToLink);
                byte[] linkInBytes = Arrays.copyOfRange(descInBytes, start, end);
                System.arraycopy(linkInBytes, 0, sizeofDescByteToLinkWithLeftBracketAndContent, sizeofDescByteToLink, end - start);
                sizeofDescByteToLinkWithLeftBracketAndContent[sizeofDescByteToLinkWithLeftBracketAndContent.length - 2] = ']';
                sizeofDescByteToLinkWithLeftBracketAndContent[sizeofDescByteToLinkWithLeftBracketAndContent.length - 1] = '(';
                byte[] linkUriInBytes = facet.getJSONArray("features").getJSONObject(0).getString("uri").getBytes();
                byte[] descWithURI = new byte[sizeofDescByteToLinkWithLeftBracketAndContent.length + linkUriInBytes.length + 1];
                System.arraycopy(sizeofDescByteToLinkWithLeftBracketAndContent, 0, descWithURI, 0, sizeofDescByteToLinkWithLeftBracketAndContent.length);
                System.arraycopy(linkUriInBytes, 0, descWithURI, sizeofDescByteToLinkWithLeftBracketAndContent.length, linkUriInBytes.length);
                descWithURI[descWithURI.length - 1] = ')';
                byte[] restOfDescInBytes = Arrays.copyOfRange(descInBytes, end, descInBytes.length);
                desc = new StringBuilder(new String(descWithURI, StandardCharsets.UTF_8));
                desc.append(new String(restOfDescInBytes, StandardCharsets.UTF_8));
                shiftedDesc += 4 + facet.getJSONArray("features").getJSONObject(0).getString("uri").getBytes().length;
            }
        }

        return desc.toString();
    }

    @Override
    public String name() {
        return "bSkyTracker";
    }
}
