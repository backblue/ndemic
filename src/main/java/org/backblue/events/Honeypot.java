package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.backblue.core.IO;
import org.backblue.utilities.FeatureFlag;
import org.backblue.utilities.MessagePriority;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Honeypot extends MessagePriority {
    final String pingRole;

    public Honeypot(int priority, Bot bot, String pingRole) {
        super(priority, bot);
        this.pingRole = pingRole;
    }

    @Override
    public boolean run(MessageReceivedEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.Honeypot)) {
            if (!event.isFromGuild() && !bot.getDeploymentGuild().getId().equals(event.getGuild().getId())) {
                return false;
            }
            if (!event.getMessage().getChannel().getId().equals(bot.getIO().getChannel(IO.DefinedChannel.DeploymentHoney).getId()) ) {
                return false;
            }
            if (event.getMember() == null) {
                return false;
            }
            if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                return false;
            }
            event.getMessage().delete().queue();
            event.getMember().timeoutFor(12, TimeUnit.HOURS).reason("Posted in Restricted Channel").queue();
            bot.getIO().send(event.getMember().getUser(), "Hello, your recent activity has been flagged for additional review by our moderators. You have been temporarily timed out as we review this situation. We apologize for the inconvenience.");
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Someone posted in the forbidden channel...");
            embedBuilder.setThumbnail(event.getMember().getEffectiveAvatarUrl());
            embedBuilder.setDescription(event.getMessage().getContentStripped());
            embedBuilder.setFooter(event.getMessage().getAttachments().size() + " attachment(s), removed 60 mins of messages, 12 hour timeout");

            List<FileUpload> fileUploads = new ArrayList<>();
            if (!event.getMessage().getAttachments().isEmpty()) {
                for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                    fileUploads.add(attachment.getProxy().downloadAsFileUpload(attachment.getFileName()));
                }
            }

            bot.getIO().send(IO.DefinedChannel.DeploymentBotCommands, "<@" + pingRole + "> - " + event.getMember().getAsMention(),
                    embedBuilder.build(),
                    fileUploads);

            //todo: purge old messages

            return true;
        }
        return false;
    }
}
