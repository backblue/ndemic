package org.backblue.wrappers;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ListModelsConfig;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Gemini {

    private static final Logger Log = LoggerFactory.getLogger(Gemini.class);

    final Bot bot;
    final String key;
    final String[] models;
    final Client client;

    public Gemini(@NotNull Bot bot, String key, JSONObject config) {
        this.bot = bot;
        if (key == null || config == null) {
            this.key = null;
            this.models = null;
            this.client = null;
            Log.warn("Missing gemini key and config, disabling AI features");
            bot.disableFeature(FeatureFlag.Gemini);
            return;
        }
        this.key = key;
        this.client = Client.builder().apiKey(key).build();
        if (config.optJSONObject("models") == null) {
            models = null;
        } else {
            models = new String[config.getJSONArray("models").length()];
            for (int i = 0; i < models.length; i++) {
                models[i] = config.getJSONArray("models").getString(i);
            }
        }
        try {
            client.models.list(ListModelsConfig.builder().build());
        } catch (Exception e) {
            Log.error("Failed to validate Gemini API key, disabling AI features");
            bot.disableFeature(FeatureFlag.Gemini);
        }
    }

    public ReturnJSON inputString(String prompt, GenerateContentConfig config) {
        for (String model : models) {
            try (Client client = Client.builder().apiKey(key).build()) {
                return new ReturnJSON(true, client.models.generateContent(
                        model,
                        prompt,
                        config));
            } catch (Exception ignored) {}
        }
        Log.warn("Failure to receive any response from Google (models busy?)");
        return new ReturnJSON(false, null);
    }

    public record ReturnJSON(boolean success, @Nullable GenerateContentResponse response) {}

}
