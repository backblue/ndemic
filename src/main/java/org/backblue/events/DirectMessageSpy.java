package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DirectMessageSpy extends ListenerAdapter {

    private static final String DM_CHANNEL_ID = "848994984023687238";

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        if (event.getMessage().getContentRaw().isEmpty()) {
            return;
        }

        MessageEmbed message = new EmbedBuilder()
                .setColor(Color.CYAN)
                .setTitle("DM Received")
                .addField("Message", event.getMessage().getContentRaw(), false)
                .addField("Author", event.getAuthor().getName(), false)
                .build();

        TextChannel channel = event.getJDA().getTextChannelById(DM_CHANNEL_ID);

        channel.sendMessageEmbeds(message).queue();

    }
}
