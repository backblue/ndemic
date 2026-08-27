package org.backblue.moderation;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.backblue.enums.FeatureFlag;
import org.backblue.utilities.MessagePriority;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Autoresponding extends MessagePriority {

    final List<AutoresponderMessage> messages = Collections.synchronizedList(new ArrayList<>());
    final List<AutoresponderEmoji> emojis = Collections.synchronizedList(new ArrayList<>());

    public Autoresponding(int priority, Bot bot, JSONObject config) {
        super(priority, bot);
        if (config == null) {
            bot.disableFeature(FeatureFlag.Autoresponder);
            return;
        }

        JSONArray messages = config.optJSONArray("messageResponse", null);
        JSONArray emojis = config.optJSONArray("reactionResponse", null);
        if (messages != null) {
            messages.forEach(obj -> {
                if ((obj instanceof JSONObject json)) {
                    String text = json.optString("keyword", null);
                    String emoji = json.optString("response", null);
                    if (text != null && emoji != null) {
                        this.messages.add(new AutoresponderMessage(text, emoji));
                    }
                }
            });
        }
        if (emojis != null) {
            emojis.forEach(obj -> {
                if ((obj instanceof JSONObject json)) {
                    String keyword = json.optString("keyword", null);
                    String emoji = json.optString("emoji", null);
                    if (keyword != null && emoji != null) {
                        this.emojis.add(new AutoresponderEmoji(keyword, emoji));
                    }
                }
            });
        }

    }

    public List<AutoresponderMessage> getMessages() {
        return messages;
    }
    public List<AutoresponderEmoji> getEmojis() {
        return emojis;
    }
    public List<AutoresponderEntry> getLibrary() {
        List<AutoresponderEntry> entries = new ArrayList<>(this.emojis);
        entries.addAll(this.messages);
        return entries;
    }

    /**
     * In order, determined by priority, to see what events should be fired first.
     *
     * @param event {@code MessageReceivedEvent} event.
     * @return {@code true} if event is 'canceled', then no other listener that has priority above the current will receive this event.
     */
    @Override
    public boolean cancelled(MessageReceivedEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.Autoresponder) && event.getAuthor().isBot()) return false;

        for (AutoresponderMessage m : messages) {
            if (event.getMessage().getContentRaw().contains(m.keyword)) {
                bot.getIO().send(event.getChannel().getId(), m.response);
                return false;
            }
        }
        for (AutoresponderEmoji emoji : emojis) {
            if (event.getMessage().getContentRaw().contains(emoji.keyword)) {
                Emoji emote = Emoji.fromFormatted(emoji.emoji);
                event.getMessage().addReaction(emote).queue();
                return false;
            }
        }
        return false;
    }

    public interface AutoresponderEntry {}
    public record AutoresponderMessage(String keyword, String response) implements AutoresponderEntry {}
    public record AutoresponderEmoji(String keyword, String emoji) implements AutoresponderEntry {}
}
