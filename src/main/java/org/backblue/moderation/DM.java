package org.backblue.moderation;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.jetbrains.annotations.NotNull;

public final class DM extends ListenerAdapter {

    Bot bot;

    public DM(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isFromType(ChannelType.PRIVATE) && !event.getMessage().getContentRaw().isEmpty()) {

            if (bot.getIO().getChannel(DefinedChannel.DebugDirectMessages) instanceof GuildMessageChannel c) {
                bot.getIO().send(DefinedChannel.DebugDirectMessages, "From " + event.getAuthor().getName() + " / `" + event.getAuthor().getId() + "`:");
                event.getMessage().forwardTo(c).queue();
            }

        }
    }

}
