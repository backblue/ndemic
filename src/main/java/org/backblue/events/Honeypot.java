package org.backblue.events;

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


            event.getMember().timeoutFor(12, TimeUnit.HOURS).reason("Posted in Honeypot Channel").queue();

            GuildChannel c = bot.getIO().getChannel(DefinedChannel.DeploymentHoney);
            if (!(c instanceof GuildMessageChannel messageChannel)) {
                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle("Someone posted in the honeypot channel...")
                        .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                        .setDescription(event.getMessage().getContentStripped())
                        .setFooter(event.getMessage().getAttachments().size() + " attachment(s), applied 12 hour timeout");

                bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getName() + " - " + event.getMember().getAsMention(),
                        embedBuilder.build(),
                        bot.toUploads(event.getMessage().getAttachments()));
            } else {
                event.getMessage().forwardTo(messageChannel).queue();
                bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getName() + " - " + event.getMember().getAsMention() + " posted in honeypot");
            }

            event.getMessage().delete().queue();
            bot.getIO().clean(event.getMember());
            return true;
        }
        return false;
    }
}
