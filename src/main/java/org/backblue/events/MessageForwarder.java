package org.backblue.events;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.utilities.NdemicModule;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MessageForwarder extends ListenerAdapter implements NdemicModule {

    Map<String, String> keyWordToChannelId = new HashMap<>();
    Map<String, String> keyWordToPingId = new HashMap<>();

    @Override
    public String name() {
        return "messageForwarding";
    }

    public MessageForwarder(JSONArray json) {
        if (json == null) {
            return;
        }
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
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (isEnabled() && event.isFromGuild() && !event.getAuthor().isBot()) {
            for (String keyWord : keyWordToChannelId.keySet()) {
                if (event.getMessage().getContentRaw().toLowerCase().contains(keyWord.toLowerCase())) {
                    event.getJDA().getTextChannelById(keyWordToChannelId.get(keyWord)).sendMessage("<@&"+keyWordToPingId.get(keyWord)+"> Forwarded message: \n>>> " + event.getMessage().getContentRaw() + "\n<#" + event.getChannel().getId() + ">").queue();
                }
            }
        }
    }
}
