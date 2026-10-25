package org.backblue.cloud;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class GoogleVision {

    private static final URI ENDPOINT = URI.create("https://vision.googleapis.com/v1/images:annotate");

    private final String key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GoogleVision(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Google Vision API key is required");
        this.key = key;
    }

    /** WEB_DETECTION: what the image is and where it appears online. Blocking; call off the JDA event thread. */
    public WebContext webDetection(byte[] image, int maxResults) throws IOException, InterruptedException {
        JSONObject request = new JSONObject()
                .put("image", new JSONObject().put("content", Base64.getEncoder().encodeToString(image)))
                .put("features", new JSONArray().put(new JSONObject().put("type", "WEB_DETECTION").put("maxResults", maxResults)));
        JSONObject response = annotate(request);
        return parseWebDetection(response.optJSONObject("webDetection", new JSONObject()));
    }

    // Sends one annotate request and returns its single response, throwing on API errors.
    private JSONObject annotate(JSONObject request) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(new JSONObject().put("requests", new JSONArray().put(request)).toString()))
                .build();
        HttpResponse<String> httpResponse = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        JSONObject body;
        try {
            body = new JSONObject(httpResponse.body());
        } catch (Exception e) {
            throw new IOException("Google Vision returned HTTP " + httpResponse.statusCode() + " with an unreadable body");
        }
        JSONObject error = body.optJSONObject("error");
        if (httpResponse.statusCode() != 200 || error != null) {
            String message = error != null ? error.optString("message", "unknown error") : "unknown error";
            throw new IOException("Google Vision returned HTTP " + httpResponse.statusCode() + ": " + message);
        }

        JSONArray responses = body.optJSONArray("responses");
        JSONObject response = responses != null && !responses.isEmpty() ? responses.getJSONObject(0) : new JSONObject();
        JSONObject responseError = response.optJSONObject("error");
        if (responseError != null) {
            throw new IOException("Google Vision could not process the image: " + responseError.optString("message", "unknown error"));
        }
        return response;
    }

    static WebContext parseWebDetection(JSONObject web) {
        List<String> labels = new ArrayList<>();
        JSONArray bestGuesses = web.optJSONArray("bestGuessLabels", new JSONArray());
        for (int i = 0; i < bestGuesses.length(); i++) {
            String label = bestGuesses.getJSONObject(i).optString("label", "");
            if (!label.isBlank()) labels.add(label);
        }

        List<Entity> entities = new ArrayList<>();
        JSONArray webEntities = web.optJSONArray("webEntities", new JSONArray());
        for (int i = 0; i < webEntities.length(); i++) {
            JSONObject entity = webEntities.getJSONObject(i);
            String description = entity.optString("description", "");
            if (!description.isBlank()) entities.add(new Entity(description, entity.optDouble("score", 0)));
        }

        List<Page> pages = new ArrayList<>();
        JSONArray matchingPages = web.optJSONArray("pagesWithMatchingImages", new JSONArray());
        for (int i = 0; i < matchingPages.length(); i++) {
            JSONObject page = matchingPages.getJSONObject(i);
            String url = page.optString("url", "");
            // Titles come back with <b> highlighting around matched terms.
            if (!url.isBlank()) pages.add(new Page(url, page.optString("pageTitle", "").replaceAll("<[^>]+>", "").strip()));
        }

        return new WebContext(labels, entities, pages,
                urls(web.optJSONArray("fullMatchingImages")),
                urls(web.optJSONArray("partialMatchingImages")),
                urls(web.optJSONArray("visuallySimilarImages")));
    }

    private static List<String> urls(JSONArray images) {
        List<String> urls = new ArrayList<>();
        if (images == null) return urls;
        for (int i = 0; i < images.length(); i++) {
            String url = images.getJSONObject(i).optString("url", "");
            if (!url.isBlank()) urls.add(url);
        }
        return urls;
    }

    public record WebContext(List<String> bestGuessLabels, List<Entity> entities, List<Page> pages,
                             List<String> fullMatches, List<String> partialMatches, List<String> similarImages) {}

    /** @param score relevance as reported by Google; not a probability and not capped at 1. */
    public record Entity(String description, double score) {}

    public record Page(String url, String title) {}
}
