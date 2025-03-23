package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class PrivateMessage extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        if (!Core.MODULES.get("dmSpy")) {
            return;
        }


        try {
            event.getGuild().getName();
        } catch (IllegalStateException e) {

            if (event.isFromGuild()) {
                return;
            }

            if (event.getMessage().getContentRaw().isEmpty()) {
                return;
            }

            if (event.getMember() == null) {
                return;
            }

            if (event.getMember().getUser().isBot()) {
                return;
            }

            MessageEmbed message = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setTitle("DM Received")
                    .addField("Message", event.getMessage().getContentRaw(), false)
                    .addField("Author", event.getAuthor().getName(), false)
                    .build();

            TextChannel channel = event.getJDA().getTextChannelById(Core.SETTINGS.getString("loggingChannel"));

            channel.sendMessageEmbeds(message).queue();
        }
    }
}
