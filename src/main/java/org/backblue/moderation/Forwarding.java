package org.backblue.moderation;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.backblue.utilities.MessagePriority;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class Forwarding extends MessagePriority {

    private static final Logger Log = LoggerFactory.getLogger(Forwarding.class);

    Map<String, String> keyWordToChannelId = new HashMap<>();
    Map<String, String> keyWordToPingId = new HashMap<>();

    public Forwarding(int priority, Bot bot, JSONArray json) {
        super(priority, bot);
        if (json == null) {
            Log.error("Missing/Unknown key-object in config. Messages will not be forwarded");
            bot.disableFeature(FeatureFlag.MessageForwarding);
            return;
        }
        for (int i =0;i< json.length();i++) {
            JSONObject entry = json.getJSONObject(i);
            if (!(entry.has("match") && entry.has("sendToInAnalysisChannel") && entry.has("pingRoleIdInAnalysisChannel"))) {
                Log.error("Invalid entry, {}, in messageForwarding config, skipping", i);
                continue;
            }
            Log.info("Added keyword '{}' forward to channel #{} with ping role @{}", entry.getString("match"), entry.getString("sendToInAnalysisChannel"), entry.getString("pingRoleIdInAnalysisChannel"));
            keyWordToChannelId.put(entry.getString("match"), entry.getString("sendToInAnalysisChannel"));
            keyWordToPingId.put(entry.getString("match"), entry.getString("pingRoleIdInAnalysisChannel"));
        }
    }

    @Override
    public boolean cancelled(MessageReceivedEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.MessageForwarding) && event.isFromGuild() && !event.getAuthor().isBot()) {
            for (String keyWord : keyWordToChannelId.keySet()) {
                if (event.getMessage().getContentRaw().toLowerCase().contains(keyWord.toLowerCase())) {
                    TextChannel c = event.getJDA().getTextChannelById(keyWordToChannelId.get(keyWord));
                    if (c != null) {
                        event.getMessage().forwardTo(c).queue();
                        c.sendMessage("<@&"+keyWordToPingId.get(keyWord)+">)").queue();
                    }
                    // Objects.requireNonNull(event.getJDA().getTextChannelById(keyWordToChannelId.get(keyWord))).sendMessage("<@&"+keyWordToPingId.get(keyWord)+"> Forwarded message: \n>>> " + event.getMessage().getContentRaw() + "\n<#" + event.getChannel().getId() + ">").queue();
                }
            }
        }
        return false;
    }
}
