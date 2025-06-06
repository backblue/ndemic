package org.backblue.events;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

public class AutoModAlert extends ListenerAdapter {
    @Override
    public void onAutoModExecution(@NotNull AutoModExecutionEvent event) {
        if (Bot.getBot().getModuleValue("discordAutoModNotify")) {
            if (event.getChannel() == null || event.getAlertMessageId() == null) {
                return;
            }
            TextChannel channel = event.getJDA().getTextChannelById(Bot.getBot().getDeployment().get("channels.cmd"));
            Role role = event.getJDA().getRoleById(Bot.getBot().getDeployment().get("channels.optIn"));
            if (channel != null && role != null) {
                channel.sendMessage(role.getAsMention()).queue();
                Bot.getBot().sendDebugMessage("autoMod", "AutoMod @ Mods pinged for violation: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + channel.getId() + "/" + event.getAlertMessageId());
            }
        }
    }
}
