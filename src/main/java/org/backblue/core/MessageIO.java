package org.backblue.core;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.events.CryptoDetection;
import org.backblue.events.OneAuthorGuide;
import org.backblue.events.Forwarding;
import org.backblue.events.Honeypot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.MessagePriority;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class MessageIO extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(MessageIO.class);

    private ShardManager JDA;
    private final Map<DefinedChannel, String> mapping;
    private final PriorityQueue<MessagePriority> messageQueue = new PriorityQueue<>();
    private final Map<String, Deque<Message>> recentMessages = new ConcurrentHashMap<>();
    private int prevMessagesLoggingLimit;

    public void send(DefinedChannel dest, String text) {
        GuildChannel targetChannel = this.JDA.getGuildChannelById(mapping.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
    }

    public void send(DefinedChannel dest, String text, MessageEmbed embed) {
        GuildChannel targetChannel = this.JDA.getGuildChannelById(mapping.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).setEmbeds(embed).queue();
    }

    public void send(DefinedChannel dest, String text, MessageEmbed embed, List<FileUpload> fileUploads) {
        GuildChannel targetChannel = this.JDA.getGuildChannelById(mapping.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).addFiles(fileUploads).setEmbeds(embed).queue();
    }

    public void send(DefinedChannel dest, String text, List<FileUpload> fileUploads) {
        GuildChannel targetChannel = this.JDA.getGuildChannelById(mapping.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).addFiles(fileUploads).queue();
    }

    public void send(User user, String text) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(text).queue());
    }

    public void send(User user, String text, MessageEmbed embed) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(text).setEmbeds(embed).queue());
    }

    public void send(String textChannelID, String text) {
        GuildChannel targetChannel = this.JDA.getGuildChannelById(textChannelID);
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
    }

    public void send(DefinedChannel dest, String text, Container container) {
        GuildChannel targetChannel = this.JDA.getGuildChannelById(mapping.get(dest));
        if (!text.isEmpty() && targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessageComponents(container).useComponentsV2(true).queue();
    }

    public GuildChannel getChannel(DefinedChannel dest) {
        return this.JDA.getTextChannelById(mapping.get(dest));
    }

    public void clean(Member member) {
        Deque<Message> messages = this.recentMessages.remove(member.getId());
        if (messages == null) return;
        for (Message message : messages) {
            message.delete().queue();
        }
    }

    public void setJDA(ShardManager JDA) {
        if (JDA != null) this.JDA = JDA;
    }

    public MessageIO(JSONObject settings, Bot bot) {
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
            prevMessagesLoggingLimit = 16;
        }
        messageQueue.add(new OneAuthorGuide(50, bot));
        messageQueue.add(new Honeypot(5, bot));
        messageQueue.add(new Forwarding(15, bot, settings.optJSONArray("messageForwarding")));
        messageQueue.add(new CryptoDetection(6, bot, settings.optJSONObject("detectCrypto")));
    }

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.isFromGuild() && !event.getAuthor().isBot()) {
            for (MessagePriority listener : messageQueue) if (listener.cancelled(event)) break;
            Deque<Message> messages = this.recentMessages.computeIfAbsent(
                    event.getAuthor().getId(),
                    id -> new ConcurrentLinkedDeque<>());
            messages.addLast(event.getMessage());
            if (messages.size() >= this.prevMessagesLoggingLimit) {
                messages.removeFirst();
            }
        }
    }
}
