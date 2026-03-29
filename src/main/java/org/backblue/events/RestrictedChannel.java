package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class RestrictedChannel extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (Bot.getBot().getModuleValue("restrictedChannel")) {
            if (!event.isFromGuild() && !Bot.getBot().getDeploymentGuild().getId().equals(event.getGuild().getId())) {
                return;
            }
            if (!event.getMessage().getChannel().getId().equals(Bot.getBot().getDeployment().get("channels.restricted")) ) {
                return;
            }
            Message message = event.getMessage();
            Bot.getBot().getScheduler().schedule(() -> {
                // Try to fetch the message again
                message.getChannel().retrieveMessageById(message.getId()).queue(
                        retrieved -> {
                            // Message still exists: safe to process
                            process(event);
                        },
                        failure -> {}
                );
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    private void process(MessageReceivedEvent event) {
        if (event.getMember() == null) {
            return;
        }
        if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            return;
        }
        event.getMessage().delete().queue();
        event.getMember().timeoutFor(12, TimeUnit.HOURS).reason("Posted in Restricted Channel").queue();
        Bot.getBot().sendUserMessage(event.getMember().getUser(), "Hello, your recent activity has been flagged for additional review by our moderators. You have been temporarily timed out as we review this situation. We apologize for the inconvenience.");
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Someone posted in the honeypot channel...");
        embedBuilder.setThumbnail(event.getMember().getEffectiveAvatarUrl());
        embedBuilder.setDescription(event.getMessage().getContentStripped());
        embedBuilder.setFooter(event.getMessage().getAttachments().size() + "  attachment(s), removed 60 mins of messages, 12 hour timeout");
        for (int i = 0; i < event.getMessage().getAttachments().size(); i++) {
            Bot.getBot().sendDebugMessage("attachments", "Attachment " + (i + 1) + " for " + event.getMember().getAsMention() + "\n" + event.getMessage().getAttachments().get(i).getProxyUrl());
            embedBuilder.addField("Attachment " + (i + 1), event.getMessage().getAttachments().get(i).getProxyUrl(), false);
        }
        Bot.getBot().sendDeploymentMessage("cmd", Bot.getBot().getMostModerators().getAsMention() + " - " + event.getMember().getAsMention(), embedBuilder.build());
        Bot.getBot().purgeMessages(event.getMember(), 1);
    }

}
