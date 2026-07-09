package org.backblue.core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.cloud.CryptoDetection;
import org.backblue.moderation.OneAuthorGuide;
import org.backblue.moderation.Forwarding;
import org.backblue.moderation.Honeypot;
import org.backblue.utilities.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public final class MessageIO extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(MessageIO.class);

    private final Bot bot;
    private final Map<DefinedChannel, String> mapping;
    private final PriorityQueue<MessagePriority> messageQueue = new PriorityQueue<>();
    private final Map<String, Deque<Message>> recentMessages = new ConcurrentHashMap<>();
    private final Map<String, Long> userLastActivity = new ConcurrentHashMap<>();
    private int prevMessagesLoggingLimit;
    private static final byte USER_INACTIVE_TIMEOUT_MIN = 60;

    public void send(DefinedChannel dest, String text) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(mapping.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
    }

    public void send(DefinedChannel dest, String text, MessageEmbed embed) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(mapping.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).setEmbeds(embed).queue();
    }

    public void send(DefinedChannel dest, String text, MessageEmbed embed, List<FileUpload> fileUploads) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(mapping.get(dest));
        if (embed == null && targetChannel instanceof MessageChannelUnion messageChannel) {
            messageChannel.sendMessage(text).addFiles(fileUploads).queue();
            return;
        }
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).addFiles(fileUploads).setEmbeds(embed).queue();
    }

    public void send(DefinedChannel dest, String text, List<FileUpload> fileUploads) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(mapping.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).addFiles(fileUploads).queue();
    }

    public void send(User user, String text) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(text).queue(
                success -> {},
                failure -> {}
        ));
    }

    public void send(User user, String text, MessageEmbed embed) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(text).setEmbeds(embed).queue(
                success -> {},
                failure -> {}
        ));
    }

    public void send(String textChannelID, String text) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(textChannelID);
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
    }

    public void send(DefinedChannel dest, String text, Container container) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(mapping.get(dest));
        if (!text.isEmpty() && targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessageComponents(container).useComponentsV2(true).queue();
    }

    public GuildChannel getChannel(DefinedChannel dest) {
        return this.bot.getJDA().getTextChannelById(mapping.get(dest));
    }
    public void clean(String id) {
        Deque<Message> messages = this.recentMessages.remove(id);
        if (messages == null || messages.isEmpty()) return;
        if (messages.size() == 1) {
            messages.getFirst().delete().queue();
        }

        for (Message message : messages) {
            message.delete().queue(
                    success -> {},
                    failure -> {
                        if (failure instanceof ErrorResponseException f && f.getErrorCode() != 10008) {
                            Log.error("Unhandled error: ", failure);
                        }
                    }
            );
        }
    }

    public MessageIO(JSONObject settings, Bot bot, Properties props) {
        this.bot = bot;
        this.mapping = new EnumMap<>(DefinedChannel.class);
        JSONObject settingsChannel = settings.optJSONObject("channels", null);
        if (settingsChannel == null) {
            Log.error("Missing \"channels\" key-object in config. Non-interaction responses will not be sent");
            return;
        }
        for (DefinedChannel channel : DefinedChannel.values()) {
            try {
                mapping.put(channel, settingsChannel.getString(channel.configKey()));
            } catch (JSONException e) {
                mapping.put(channel, null);
                Log.error("Key '{}' unable to be read. Messages to that channel will not be sent", channel.configKey());
            }
        }
        try {
            prevMessagesLoggingLimit = settingsChannel.getInt("_track");
        } catch (Exception e) {
            prevMessagesLoggingLimit = 8;
        }
        messageQueue.add(new OneAuthorGuide(50, bot));
        messageQueue.add(new Honeypot(5, bot));
        messageQueue.add(new Forwarding(15, bot, settings.optJSONArray("messageForwarding")));
        messageQueue.add(new CryptoDetection(6, bot, settings.optJSONObject("detectCrypto"), (String) props.getOrDefault("VISION_ENDPOINT", null), (String) props.getOrDefault("VISION_KEY", null)));
        bot.getScheduler().scheduleAtFixedRate(this::cleanupInactiveUsers, 1, 1, TimeUnit.HOURS);
    }

    private void cleanupInactiveUsers() {
        long now = System.currentTimeMillis();
        userLastActivity.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > USER_INACTIVE_TIMEOUT_MIN * 60 * 1000) {
                recentMessages.remove(entry.getKey());
                return true;
            }
            return false;
        });
        Log.info("Cleaned up inactive users. Storing {} users", recentMessages.size());
    }

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.isFromGuild() && !event.getAuthor().isBot()) {
            for (MessagePriority listener : messageQueue) if (listener.cancelled(event)) break;
            if (event.getMember() != null && event.getMember().hasPermission(Permission.ADMINISTRATOR)) return;
            String userId = event.getAuthor().getId();
            Deque<Message> messages = this.recentMessages.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
            messages.addLast(event.getMessage());
            if (messages.size() >= this.prevMessagesLoggingLimit) {
                messages.removeFirst();
            }
            userLastActivity.put(userId, System.currentTimeMillis());
        }
    }
}
