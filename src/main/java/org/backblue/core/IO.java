package org.backblue.core;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.events.EnforceGuide;
import org.backblue.events.Forwarding;
import org.backblue.events.Honeypot;
import org.backblue.utilities.EventPriority;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class IO extends ListenerAdapter {

    private ShardManager JDA;
    private final Map<DefinedChannel, String> mapping;
    private final PriorityQueue<EventPriority> queue = new PriorityQueue<>();

    public enum DefinedChannel {
        DebugDirectMessages,
        DebugAutoModAlert,
        DebugEnforcement,
        DebugImageDump,
        DeploymentBotCommands,
        DeploymentWarnings,
        DeploymentLogs,
        DeploymentHoney
    }

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


    public GuildChannel getChannel(DefinedChannel dest) {
        return this.JDA.getTextChannelById(mapping.get(dest));
    }

    public void setJDA(ShardManager JDA) {
        if (JDA != null) this.JDA = JDA;
    }

    public IO(JSONObject settings, Bot bot) {
        this.mapping = new HashMap<>();
        JSONObject settingsChannel = settings.optJSONObject("channels", null);
        if (settingsChannel == null) {
            System.err.println("Missing \"channels\" key in config. No non-interaction responses will be sent.");
            return;
        }
        mapping.put(DefinedChannel.DebugDirectMessages, settingsChannel.optString("debugDirectMessages", null));
        mapping.put(DefinedChannel.DebugAutoModAlert, settingsChannel.optString("debugAutoModAlert", null));
        mapping.put(DefinedChannel.DebugEnforcement, settingsChannel.optString("debugEnforcement", null));
        mapping.put(DefinedChannel.DebugImageDump, settingsChannel.optString("debugImageDump", null));
        mapping.put(DefinedChannel.DeploymentBotCommands, settingsChannel.optString("deploymentBotCmds", null));
        mapping.put(DefinedChannel.DeploymentWarnings, settingsChannel.optString("deploymentWarnings", null));
        mapping.put(DefinedChannel.DeploymentLogs, settingsChannel.optString("deploymentLogs", null));
        mapping.put(DefinedChannel.DeploymentHoney, settingsChannel.optString("deploymentHoney", null));

        queue.add(new EnforceGuide(50, bot));
        queue.add(new Honeypot(10, bot, bot.pingRoleID));
        queue.add(new Forwarding(15, bot, settings.getJSONArray("messageForwarding")));
    }

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.isFromGuild()) for (EventPriority listener : queue) if (listener.run(event)) break;
    }
}
