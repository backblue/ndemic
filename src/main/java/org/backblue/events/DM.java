package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DM extends ListenerAdapter {

    Bot bot;

    public DM(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isFromType(ChannelType.PRIVATE) && !event.getAuthor().isBot() && !event.getMessage().getContentRaw().isEmpty()) {
            MessageEmbed message = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setTitle("DM Received")
                    .addField("Message", event.getMessage().getContentRaw(), false)
                    .addField("Author", event.getAuthor().getName() + "/" + event.getAuthor().getId(), false)
                    .build();
            bot.getIO().send(DefinedChannel.DebugDirectMessages, "", message);
        }
    }

}
