package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.backblue.core.IO;
import org.backblue.utilities.FeatureFlag;
import org.backblue.utilities.EventPriority;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Honeypot extends EventPriority {
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
            purge(event.getMember());
            return true;
        }
        return false;
    }

    private void purge(Member member) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);
        AtomicInteger remaining = new AtomicInteger(100);

        List<GuildMessageChannel> channels = new ArrayList<>();
        channels.addAll(member.getGuild().getTextChannels());
        channels.addAll(member.getGuild().getThreadChannels());

        for (GuildMessageChannel channel : channels) {
            if (remaining.get() <= 0)
                break;

            if (!member.hasPermission(channel, Permission.VIEW_CHANNEL))
                continue;

            channel.getHistory().retrievePast(100).queue(messages -> {

                List<Message> toDelete = new ArrayList<>();

                for (Message m : messages) {
                    if (remaining.get() <= 0)
                        break;

                    if (!m.getAuthor().getId().equals(member.getId()))
                        continue;

                    if (m.getTimeCreated().isBefore(cutoff))
                        continue;

                    toDelete.add(m);
                    remaining.decrementAndGet();
                }

                if (toDelete.size() >= 2) {
                    if (channel instanceof TextChannel tc) {
                        tc.deleteMessages(toDelete).queue();
                    } else if (channel instanceof ThreadChannel thread) {
                        toDelete.forEach(m -> m.delete().queue());
                    } else {
                        toDelete.forEach(m -> m.delete().queue());
                    }
                }
                else if (toDelete.size() == 1) {
                    toDelete.getFirst().delete().queue();
                }
            });
        }
    }
}
