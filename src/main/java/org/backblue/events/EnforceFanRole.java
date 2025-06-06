package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class EnforceFanRole extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (Bot.getBot().getModuleValue("enforceFanRole")) {
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
        if (event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)|| event.getMember() == null || !event.getAuthor().isBot() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            return;
        }
        if (event.getGuild().getId().equals(Bot.getBot().getDeployment().get("guild"))) {
            for (Role role : event.getMember().getRoles()) {
                if (role.getName().contains("Fan")) {
                    return;
                }
                try {
                    event.getMessage().delete().queue();
                } catch (Exception ignored) {}
                Bot.getBot().sendUserMessage(event.getMember().getUser(), Bot.getBot().getDeployment().get("fanRoleFailureMsg"));
                Bot.getBot().sendDebugMessage("enforceFan", "Prevented user `" + event.getMember().getEffectiveName() + "` (" + event.getMember().getAsMention() + ") from sending a message in " + event.getChannel().getName() + " due to no 'fan' roles.");
            }

        }
    }
}
