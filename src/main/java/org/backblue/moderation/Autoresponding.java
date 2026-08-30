package org.backblue.moderation;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.backblue.enums.FeatureFlag;
import org.backblue.utilities.MessagePriority;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                    boolean exact = json.optBoolean("exact", false);
                    if (text != null && emoji != null) {
                        this.messages.add(new AutoresponderMessage(text, emoji, exact));
                    }
                }
            });
        }
        if (emojis != null) {
            emojis.forEach(obj -> {
                if ((obj instanceof JSONObject json)) {
                    String keyword = json.optString("keyword", null);
                    String emoji = json.optString("emoji", null);
                    boolean exact = json.optBoolean("exact", false);
                    if (keyword != null && emoji != null) {
                        this.emojis.add(new AutoresponderEmoji(keyword, emoji, exact));
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
    public boolean contains(String keyword) {
        for (AutoresponderEntry entry : this.getLibrary()) {
            if (entry instanceof AutoresponderMessage m) {
                if (m.keyword.equals(keyword)) return true;
            } else if (entry instanceof AutoresponderEmoji m) {
                if (m.keyword.equals(keyword)) return true;
            }
        }
        return false;
    }
    public void insert(AutoresponderEntry entry) {
        if (entry instanceof AutoresponderMessage k) messages.add(k);
        else if (entry instanceof AutoresponderEmoji k) emojis.add(k);
    }

    public AutoresponderEntry getAt(int index) {
        int emojiCount = emojis.size();
        if (index < emojiCount) {
            return emojis.get(index);
        }
        return messages.get(index - emojiCount);
    }

    public void updateAt(int index, AutoresponderEntry updated) {
        int emojiCount = emojis.size();
        if (index < emojiCount) {
            if (!(updated instanceof AutoresponderEmoji)) {
                throw new IllegalArgumentException("Cannot replace an emoji entry with a non-emoji entry at index " + index);
            }
            emojis.set(index, (AutoresponderEmoji) updated);
        } else {
            int messageIndex = index - emojiCount;
            if (!(updated instanceof AutoresponderMessage)) {
                throw new IllegalArgumentException("Cannot replace a message entry with a non-message entry at index " + index);
            }
            messages.set(messageIndex, (AutoresponderMessage) updated);
        }
    }

    public void deleteAt(int index) {
        int emojiCount = emojis.size();
        if (index < emojiCount) {
            emojis.remove(index);
        } else {
            messages.remove(index - emojiCount);
        }
    }

    public boolean writeToJSON() {
        JSONObject json = new JSONObject();
        JSONArray messagesJson = new JSONArray();
        JSONArray emojisJson = new JSONArray();

        synchronized (messages) {
            for (AutoresponderMessage message : messages) {
                JSONObject messageJson = new JSONObject();
                messageJson.put("keyword", message.keyword());
                messageJson.put("response", message.response());
                messageJson.put("exact", message.exact());
                messagesJson.put(messageJson);
            }
        }
        synchronized (emojis) {
            for (AutoresponderEmoji emoji : emojis) {
                JSONObject emojiJson = new JSONObject();
                emojiJson.put("keyword", emoji.keyword());
                emojiJson.put("emoji", emoji.emoji());
                emojiJson.put("exact", emoji.exact());
                emojisJson.put(emojiJson);
            }
        }

        json.put("messageResponse", messagesJson);
        json.put("reactionResponse", emojisJson);
        json.put("_version", 1);
        try (FileWriter fw = new FileWriter("data/deployment-triggers.json")) {
            fw.write(json.toString(4));
            return true;
        } catch (IOException e) {
            return false;
        }
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
            if (event.getMessage().getContentRaw().equalsIgnoreCase(m.keyword)) {
                Container c = Container.of(
                        TextDisplay.of(m.response)
                );
                bot.getIO().send(event.getChannel().getId(), c);
                return false;
            }
        }
        for (AutoresponderEmoji emoji : emojis) {
            if (event.getMessage().getContentRaw().equalsIgnoreCase(emoji.keyword)) {
                Emoji emote = Emoji.fromFormatted(emoji.emoji);
                event.getMessage().addReaction(emote).queue();
                return false;
            }
        }
        return false;
    }

    public interface AutoresponderEntry {}
    public record AutoresponderMessage(String keyword, String response, boolean exact) implements AutoresponderEntry {}
    public record AutoresponderEmoji(String keyword, String emoji, boolean exact) implements AutoresponderEntry {}
}
