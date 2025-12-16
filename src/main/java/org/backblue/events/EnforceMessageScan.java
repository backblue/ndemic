package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.wrappers.MessageHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class EnforceMessageScan extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (Bot.getBot().getModuleValue("messageScanning")) {
            Message message = event.getMessage();
            if (!event.isFromGuild()) {
                return;
            }
            if (!Arrays.asList(Bot.getBot().getDeployment().get("guild"), Bot.getBot().getAnalysis().get("guild")).contains(message.getGuild().getId())) {
                return;
            }
            if (event.getMember() != null && event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                return;
            }
            Bot.getBot().getScheduler().schedule(() -> {
                // Try to fetch the message again
                message.getChannel().retrieveMessageById(message.getId()).queue(
                        retrieved -> {
                            // Message still exists: safe to process
                            process(event);
                        },
                        failure -> {}
                );
            }, 1, TimeUnit.SECONDS);
        }
    }

    private void process(MessageReceivedEvent event) {
        if (event.getMember() == null || event.getMessage().getContentRaw().isEmpty()) {
            return;
        }
        if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            return;
        }
        new MessageHandler(event.getMessage(), event.getMember());
    }
}
