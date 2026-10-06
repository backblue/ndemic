package org.backblue.moderation;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.backblue.config.Config;
import org.backblue.enums.Feature;
import org.backblue.extension.SelfEditable;
import org.backblue.utilities.MessagePriority;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Autoresponding extends MessagePriority implements SelfEditable {

    // Immutable snapshots, replaced as a whole after each successful save, so readers never see a partial edit.
    private volatile List<AutoresponderMessage> messages = List.of();
    private volatile List<AutoresponderEmoji> emojis = List.of();

    public Autoresponding(int priority, Bot bot, JSONObject config) {
        super(priority, bot);
        if (config == null) {
            bot.disableFeature(Feature.Autoresponder);
            return;
        }

        List<AutoresponderMessage> loadedMessages = new ArrayList<>();
        List<AutoresponderEmoji> loadedEmojis = new ArrayList<>();
        JSONArray messages = config.optJSONArray("messageResponse", null);
        JSONArray emojis = config.optJSONArray("reactionResponse", null);
        if (messages != null) {
            messages.forEach(obj -> {
                if ((obj instanceof JSONObject json)) {
                    String text = json.optString("keyword", null);
                    String emoji = json.optString("response", null);
                    boolean exact = json.optBoolean("exact", false);
                    if (text != null && emoji != null) {
                        loadedMessages.add(new AutoresponderMessage(text, emoji, exact));
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
                        loadedEmojis.add(new AutoresponderEmoji(keyword, emoji, exact));
                    }
                }
            });
        }
        this.messages = List.copyOf(loadedMessages);
        this.emojis = List.copyOf(loadedEmojis);

    }

    @Override
    public Scope scope() {
        return Scope.whole(Config.Deployment_Triggers_JSON);
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
    // Each edit saves first and only then replaces the in-memory lists. @return false if nothing was saved or changed.
    public synchronized boolean insert(AutoresponderEntry entry, String changedBy) {
        List<AutoresponderMessage> newMessages = new ArrayList<>(messages);
        List<AutoresponderEmoji> newEmojis = new ArrayList<>(emojis);
        if (entry instanceof AutoresponderMessage k) newMessages.add(k);
        else if (entry instanceof AutoresponderEmoji k) newEmojis.add(k);
        return commit(newMessages, newEmojis, changedBy);
    }

    public AutoresponderEntry getAt(int index) {
        List<AutoresponderEmoji> currentEmojis = emojis;
        if (index < currentEmojis.size()) {
            return currentEmojis.get(index);
        }
        return messages.get(index - currentEmojis.size());
    }

    public synchronized boolean updateAt(int index, AutoresponderEntry updated, String changedBy) {
        List<AutoresponderMessage> newMessages = new ArrayList<>(messages);
        List<AutoresponderEmoji> newEmojis = new ArrayList<>(emojis);
        int emojiCount = newEmojis.size();
        if (index < emojiCount) {
            if (!(updated instanceof AutoresponderEmoji emoji)) {
                throw new IllegalArgumentException("Cannot replace an emoji entry with a non-emoji entry at index " + index);
            }
            newEmojis.set(index, emoji);
        } else {
            if (!(updated instanceof AutoresponderMessage message)) {
                throw new IllegalArgumentException("Cannot replace a message entry with a non-message entry at index " + index);
            }
            newMessages.set(index - emojiCount, message);
        }
        return commit(newMessages, newEmojis, changedBy);
    }

    public synchronized boolean deleteAt(int index, String changedBy) {
        List<AutoresponderMessage> newMessages = new ArrayList<>(messages);
        List<AutoresponderEmoji> newEmojis = new ArrayList<>(emojis);
        int emojiCount = newEmojis.size();
        if (index < emojiCount) {
            newEmojis.remove(index);
        } else {
            newMessages.remove(index - emojiCount);
        }
        return commit(newMessages, newEmojis, changedBy);
    }

    // Saved as a whole document rather than edited per index: entries skipped while loading would shift indexes.
    private boolean commit(List<AutoresponderMessage> newMessages, List<AutoresponderEmoji> newEmojis, String changedBy) {
        if (!editable()) return false;

        JSONObject json = new JSONObject();
        JSONArray messagesJson = new JSONArray();
        JSONArray emojisJson = new JSONArray();
        for (AutoresponderMessage message : newMessages) {
            JSONObject messageJson = new JSONObject();
            messageJson.put("keyword", message.keyword());
            messageJson.put("response", message.response());
            messageJson.put("exact", message.exact());
            messagesJson.put(messageJson);
        }
        for (AutoresponderEmoji emoji : newEmojis) {
            JSONObject emojiJson = new JSONObject();
            emojiJson.put("keyword", emoji.keyword());
            emojiJson.put("emoji", emoji.emoji());
            emojiJson.put("exact", emoji.exact());
            emojisJson.put(emojiJson);
        }
        json.put("messageResponse", messagesJson);
        json.put("reactionResponse", emojisJson);
        if (!replace(json, changedBy)) return false;

        this.messages = List.copyOf(newMessages);
        this.emojis = List.copyOf(newEmojis);
        return true;
    }

    /**
     * In order, determined by priority, to see what events should be fired first.
     *
     * @param event {@code MessageReceivedEvent} event.
     * @return {@code true} if event is 'canceled', then no other listener that has priority above the current will receive this event.
     */
    @Override
    public boolean cancelled(MessageReceivedEvent event) {
        if (bot.isFeatureEnabled(Feature.Autoresponder) && event.getAuthor().isBot()) return false;

        for (AutoresponderMessage m : messages) {
            if (m.exact && event.getMessage().getContentRaw().equalsIgnoreCase(m.keyword)) {
                Container c = Container.of(
                        TextDisplay.of(m.response)
                );
                bot.getIO().send(event.getChannel().getId(), c);
                return false;
            } else if (!m.exact && event.getMessage().getContentRaw().contains(m.keyword)) {
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
