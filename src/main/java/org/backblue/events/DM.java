package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DM extends ListenerAdapter {

    Bot bot;

    public DM(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isFromType(ChannelType.PRIVATE) && !event.getAuthor().isBot() && !event.getMessage().getContentRaw().isEmpty()) {

            List<FileUpload> fileUploads = new ArrayList<>();
            if (!event.getMessage().getAttachments().isEmpty()) {
                for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                    fileUploads.add(attachment.getProxy().downloadAsFileUpload(attachment.getFileName()));
                }
            }

            MessageEmbed message = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
                    .setDescription(event.getMessage().getContentRaw())
                    .setFooter(event.getAuthor().getId() + " • " + event.getMessage().getAttachments().size() + " attachment(s)")
                    .build();
            bot.getIO().send(DefinedChannel.DebugDirectMessages, "", message, fileUploads);
        }
    }

}
