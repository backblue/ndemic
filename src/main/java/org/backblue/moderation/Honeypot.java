package org.backblue.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.FeatureFlag;
import org.backblue.utilities.MessagePriority;

import java.util.concurrent.TimeUnit;

public class Honeypot extends MessagePriority {

    public Honeypot(int priority, Bot bot) {
        super(priority, bot);
    }

    @Override
    public boolean cancelled(MessageReceivedEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.Honeypot)) {
            if (!event.isFromGuild() && !bot.getDeploymentGuild().getId().equals(event.getGuild().getId())) {
                return false;
            }
            if (!event.getMessage().getChannel().getId().equals(bot.getIO().getChannel(DefinedChannel.DeploymentHoney).getId()) ) {
                return false;
            }
            if (event.getMember() == null) {
                return false;
            }
            if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                return false;
            }

            bot.timeout(event.getMember(), "Posted in honeypot", 12, TimeUnit.HOURS);
            String validPing = event.getAuthor().getAsMention();
            GuildChannel c = bot.getIO().getChannel(DefinedChannel.DeploymentBotCommands);
            if (!(c instanceof GuildMessageChannel messageChannel)) {
                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle("Someone posted in the honeypot channel...")
                        .setDescription(event.getMessage().getContentStripped())
                        .setFooter(event.getMessage().getAttachments().size() + " attachment(s), applied 12 hour timeout");
                if (event.getMember() != null) embedBuilder.setThumbnail(event.getAuthor().getEffectiveAvatarUrl());
                bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getName() + " - " + validPing,
                        embedBuilder.build(),
                        bot.toUploads(event.getMessage().getAttachments()));
                event.getMessage().delete().queue();
                bot.getIO().clean(event.getAuthor().getId());
            } else {
                event.getMessage().forwardTo(messageChannel).queue(
                        success -> {
                            event.getMessage().delete().queue();
                            bot.getIO().clean(event.getAuthor().getId());
                        },
                        error -> {
                            event.getMessage().delete().queue();
                            bot.getIO().clean(event.getAuthor().getId());
                        }
                );
                bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getAsMention() + " - " + validPing + " posted in honeypot");
            }
            return true;
        }
        return false;
    }
}
