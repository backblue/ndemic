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
import org.backblue.events.Honeypot;
import org.backblue.utilities.MessagePriority;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class IO extends ListenerAdapter {

    private ShardManager JDA;
    private final Map<DefinedChannel, String> mapping;
    private final PriorityQueue<MessagePriority> queue = new PriorityQueue<>();

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

    public void send(User user, String text) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(text).queue());
    }

    public GuildChannel getChannel(DefinedChannel dest) {
        return this.JDA.getTextChannelById(mapping.get(dest));
    }

    public void setJDA(ShardManager JDA) {
        if (JDA != null) this.JDA = JDA;
    }

    public IO(JSONObject settings, Bot bot) {
        this.mapping = new HashMap<>();
        if (settings == null) {
            System.err.println("Missing \"channels\" key in config. No non-interaction responses will be sent.");
            return;
        }
        mapping.put(DefinedChannel.DebugDirectMessages, settings.optString("debugDirectMessages", null));
        mapping.put(DefinedChannel.DebugAutoModAlert, settings.optString("debugAutoModAlert", null));
        mapping.put(DefinedChannel.DebugEnforcement, settings.optString("debugEnforcement", null));
        mapping.put(DefinedChannel.DebugImageDump, settings.optString("debugImageDump", null));
        mapping.put(DefinedChannel.DeploymentBotCommands, settings.optString("deploymentBotCmds", null));
        mapping.put(DefinedChannel.DeploymentWarnings, settings.optString("deploymentWarnings", null));
        mapping.put(DefinedChannel.DeploymentLogs, settings.optString("deploymentLogs", null));
        mapping.put(DefinedChannel.DeploymentHoney, settings.optString("deploymentHoney", null));

        queue.add(new EnforceGuide(50, bot));
        queue.add(new Honeypot(10, bot, bot.pingRoleID));
    }

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.isFromGuild()) {
            for (MessagePriority listener : queue) {
                if (listener.run(event)) {
                    break;
                }
            }
        }
    }
}
