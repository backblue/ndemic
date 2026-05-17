package org.backblue.events;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.backblue.utilities.EventPriority;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Forwarding extends EventPriority {

    Map<String, String> keyWordToChannelId = new HashMap<>();
    Map<String, String> keyWordToPingId = new HashMap<>();

    public Forwarding(int priority, Bot bot, JSONArray json) {
        super(priority, bot);
        for (int i =0;i< json.length();i++) {
            JSONObject entry = json.getJSONObject(i);
            if (!(entry.has("match") && entry.has("sendToInAnalysisChannel") && entry.has("pingRoleIdInAnalysisChannel"))) {
                System.err.println("Invalid entry, "  + i + " in messageForwarding config, skipping");
                continue;
            }
            System.out.println("Adding message forwarder for keyword " + entry.getString("match") + " to channel " + entry.getString("sendToInAnalysisChannel") + " with ping role " + entry.getString("pingRoleIdInAnalysisChannel"));
            keyWordToChannelId.put(entry.getString("match"), entry.getString("sendToInAnalysisChannel"));
            keyWordToPingId.put(entry.getString("match"), entry.getString("pingRoleIdInAnalysisChannel"));
        }
    }

    @Override
    public boolean run(MessageReceivedEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.MessageForwarding) && event.isFromGuild() && !event.getAuthor().isBot()) {
            for (String keyWord : keyWordToChannelId.keySet()) {
                if (event.getMessage().getContentRaw().toLowerCase().contains(keyWord.toLowerCase())) {
                    Objects.requireNonNull(event.getJDA().getTextChannelById(keyWordToChannelId.get(keyWord))).sendMessage("<@&"+keyWordToPingId.get(keyWord)+"> Forwarded message: \n>>> " + event.getMessage().getContentRaw() + "\n<#" + event.getChannel().getId() + ">").queue();
                }
            }
        }
        return false;
    }
}
