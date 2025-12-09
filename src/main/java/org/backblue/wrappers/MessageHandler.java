package org.backblue.wrappers;

import com.azure.ai.contentsafety.models.AnalyzeTextOptions;
import com.azure.ai.contentsafety.models.AnalyzeTextResult;
import com.azure.ai.contentsafety.models.TextCategoriesAnalysis;
import com.azure.core.exception.HttpResponseException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Member;
import org.backblue.Bot;
import org.backblue.utilities.ComponentManager;
import org.backblue.utilities.NdemicModule;

import java.util.HashMap;

public class MessageHandler implements NdemicModule.Azure {

    @Override
    public String name() {
        return "messageScanning";
    }

    public MessageHandler() {
    }

    public MessageHandler(Message message, Member member) {
        process(message, member);
    }

    private void process(Message message, Member member) {
        if (isEnabled()) {
            HashMap<AzureProperty, Integer> analysis = processMessage(member, message.getContentRaw());
            if (analysis == null) {
                return;
            }
            for (AzureProperty category : analysis.keySet()) {
                if (analysis.get(category) >= Bot.getBot().getTasks().getJSONObject("messageScanning").getJSONObject("detection").getInt(String.valueOf(category))) {
                    if (Bot.getBot().getTasks().getJSONObject("messageScanning").getBoolean("autoDelete")) {
                        message.delete().queue();
                    }
                    if (Bot.getBot().getTasks().getJSONObject("messageScanning").getBoolean("additionalReview")) {
                        Bot.getBot().additionalReview(member, true, ComponentManager.ComponentPreset.MESSAGE, message.getContentRaw());
                    }
                    if (!(Bot.getBot().getTasks().getJSONObject("messageScanning").getBoolean("autoDelete") || Bot.getBot().getTasks().getJSONObject("messageScanning").getBoolean("additionalReview"))) {
                        Bot.getBot().sendDeploymentMessage("cmd", "Message from " + member.getUser().getAsTag() + " was flagged for " + category + " with severity " + analysis.get(category) + ". Message content: \"" + message.getContentRaw() + "\"");
                    }
                    break;
                }
            }
        }
    }

    private HashMap<AzureProperty, Integer> processMessage(Member user, String content) {
        AnalyzeTextResult response;
        try {
            for (int i = 0; i < Integer.parseInt(Bot.getBot().getDeployment().get("msgScanRemoveWordsList.size")); i++) {
                content = content.replace(Bot.getBot().getDeployment().get("msgScanRemoveWordsList." + i), "");
            }

            response = Bot.getBot().getContentSafetyClient().analyzeText(new AnalyzeTextOptions(content));
        } catch (HttpResponseException e) {
            Bot.getBot().sendDebugMessage("autoMod", "Failed to analyze msg text for " + user.getUser().getName() + " (" + user.getId() + ") due to: " + e.getMessage());
            return null;
        }
        HashMap<AzureProperty, Integer> results = new HashMap<>();
        for (TextCategoriesAnalysis analysis : response.getCategoriesAnalysis()) {
            results.put(AzureProperty.valueOf(analysis.getCategory().toString()), analysis.getSeverity());
        }
        return results;
    }


}
