package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.FeatureFlag;
import org.backblue.utilities.EventPriority;

import java.util.Objects;

public class EnforceGuide extends EventPriority {

    public EnforceGuide(int priority, Bot bot) {
        super(priority, bot);
    }

    @Override
    public boolean cancelled(MessageReceivedEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.EnforceOneGuideAccess)) {
            if (!event.isFromType(ChannelType.GUILD_PUBLIC_THREAD) || event.getMember() == null || event.getAuthor().isBot() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                return false;
            }
            ThreadChannel thread = (ThreadChannel) event.getChannel();
            if (thread.getParentChannel().getName().contains("guides")) {
                if (!Objects.equals(thread.getOwner(), event.getMessage().getMember())) {
                    try {
                        event.getMessage().delete().queue();
                    } catch (Exception ignored) {}
                    bot.getIO().send(event.getMember().getUser(), "Hi,\n\nYou can't send messages on guides that aren't yours.\n\nIf you would like to discuss about this guide, you can do them in their respective game main-channel.");
                    bot.getIO().send(DefinedChannel.DebugEnforcement, "Prevented user `" + event.getMember().getEffectiveName() + "` (" + event.getMember().getAsMention() + ") from sending a message in " + event.getChannel().getName() + " due to not being the thread owner.");
                    return true;
                }
            }
        }
        return false;
    }

}
