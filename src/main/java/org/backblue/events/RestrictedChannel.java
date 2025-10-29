package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class RestrictedChannel extends ListenerAdapter {

    public static boolean isEnabled() {
        return Bot.getBot().getModuleValue("restrictedChannels");
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (isEnabled()) {
            if (event.getChannel().getId().equals(Bot.getBot().getDeployment().get("channels.restrict")) && event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                Bot.getBot().sendDeploymentMessage("cmd", "?purge user " + event.getMember().getAsMention() + " 10");
                event.getMember().timeoutFor(30, TimeUnit.DAYS).queue();
                Bot.getBot().sendDeploymentMessage("cmd", Bot.getBot().getMostModerators().getAsMention() + " User " + event.getMember().getAsMention() + " has posted in the restricted channel.");
            }
        }
    }
}
