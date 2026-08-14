package org.backblue.utilities;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ListModelsConfig;
import org.backblue.core.Bot;
import org.backblue.enums.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GenAI {

    private static final Logger Log = LoggerFactory.getLogger(GenAI.class);

    final Bot bot;
    final String key;
    final String[] models;
    final Client client;

    public GenAI(@NotNull Bot bot, String key, JSONObject config) {
        this.bot = bot;
        if (key == null || config == null) {
            this.key = null;
            this.models = null;
            this.client = null;
            Log.warn("Missing gemini key and config, disabling AI features");
            bot.disableFeature(FeatureFlag.AI);
            return;
        }
        this.key = key;
        this.client = Client.builder().apiKey(key).build();
        if (config.optJSONArray("priorities") == null) {
            models = null;
        } else {
            models = new String[config.getJSONArray("priorities").length()];
            for (int i = 0; i < models.length; i++) {
                models[i] = config.getJSONArray("priorities").getString(i);
            }
        }
        try {
            client.models.list(ListModelsConfig.builder().build());
        } catch (Exception e) {
            Log.error("Gemini key invalid! Disabling AI features: {}", e.getMessage());
            bot.disableFeature(FeatureFlag.AI);
        }
    }

    public GenerateContentResponse inputString(String prompt, GenerateContentConfig config) {
        for (String model : models) {
            try (Client client = Client.builder().apiKey(key).build()) {
                return client.models.generateContent(
                        model,
                        prompt,
                        config);
            } catch (Exception ignored) {}
        }
        Log.warn("Failure to receive any response from Google (models busy?)");
        return null;
    }
}
