package org.backblue.moderation;

import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.enums.DefinedChannel;
import org.backblue.enums.FeatureFlag;
import org.jspecify.annotations.NonNull;

public final class AutoMod extends ListenerAdapter {

    final Bot bot;

    public AutoMod(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onAutoModExecution(@NonNull AutoModExecutionEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.AutoModAlerts)) {
            if (event.getChannel() == null || event.getAlertMessageId() == null) {
                return;
            }
            bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getAsMention());
            bot.getIO().send(DefinedChannel.DebugAutoModAlert, "AutoMod @ Mods pinged for violation: " +
            "https://discord.com/channels/" +  event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + event.getAlertMessageId());
        }
    }
}
