package org.backblue.core;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.FileUpload;
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

public final class MessageIO extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(MessageIO.class);

    private ShardManager JDA;
    private final Map<DefinedChannel, String> mapping;
    private final PriorityQueue<MessagePriority> queue = new PriorityQueue<>();

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

    public void send(DefinedChannel dest, String text, FileUpload[] fileUploads) {
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
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessageComponents(container).useComponentsV2(true).queue();
    }

    public GuildChannel getChannel(DefinedChannel dest) {
        return this.JDA.getTextChannelById(mapping.get(dest));
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
        queue.add(new OneAuthorGuide(50, bot));
        queue.add(new Honeypot(10, bot));
        queue.add(new Forwarding(15, bot, settings.optJSONArray("messageForwarding")));
    }

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.isFromGuild()) for (MessagePriority listener : queue) if (listener.cancelled(event)) break;
    }
}
