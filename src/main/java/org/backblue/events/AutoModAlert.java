package org.backblue.events;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSecurityIncidentDetectionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

public class AutoModAlert extends ListenerAdapter {

    private OffsetDateTime lastKnownDmSpamAlert = OffsetDateTime.MIN;
    private OffsetDateTime lastKnownRaidAlert = OffsetDateTime.MIN;

    @Override
    public void onAutoModExecution(@NotNull AutoModExecutionEvent event) {
        if (Bot.getBot().getModuleValue("discordAutoModNotify")) {
            if (event.getChannel() == null || event.getAlertMessageId() == null) {
                return;
            }
            TextChannel channel = event.getJDA().getTextChannelById(Bot.getBot().getDeployment().get("channels.cmd"));
            Role role = event.getJDA().getRoleById(Bot.getBot().getDeployment().get("roles.optIn"));
            if (channel != null && role != null) {
                channel.sendMessage(role.getAsMention()).queue();
                Bot.getBot().sendDebugMessage("autoMod", "AutoMod @ Mods pinged for violation: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + channel.getId() + "/" + event.getAlertMessageId());
            }
        }
    }

    @Override
    public void onGuildUpdateSecurityIncidentDetections(@NotNull GuildUpdateSecurityIncidentDetectionsEvent event) {
        if (Bot.getBot().getModuleValue("discordAutoModNotify")) {
            if (event.getNewSecurityIncidentDetections() == event.getOldSecurityIncidentDetections()) {
                return;
            }
            OffsetDateTime dm = event.getNewSecurityIncidentDetections().getTimeDetectedDmSpam();
            OffsetDateTime raid = event.getNewSecurityIncidentDetections().getTimeDetectedRaid();

            TextChannel channel = event.getJDA().getTextChannelById(Bot.getBot().getDeployment().get("channels.cmd"));
            Role role = event.getJDA().getRoleById(Bot.getBot().getDeployment().get("roles.optIn"));

            boolean ping = false;
            if (dm != null && dm.isAfter(lastKnownDmSpamAlert)) {
                lastKnownDmSpamAlert = dm;
                ping = true;
            }
            if (raid != null && raid.isAfter(lastKnownRaidAlert)) {
                lastKnownRaidAlert = raid;
                ping = true;
                SecurityIncidentActions actions = event.getGuild().getSecurityIncidentActions();
                if (actions.getInvitesDisabledUntil() == null) {
                    actions = SecurityIncidentActions.enabled(OffsetDateTime.now().plusMinutes(90), Bot.getBot().getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil());
                    Bot.getBot().getDeploymentGuild().modifySecurityIncidents(actions).queue();
                }
            }

            if (ping && channel != null && role != null) {
                channel.sendMessage(role.getAsMention()).queue();
                Bot.getBot().sendDebugMessage("autoMod", "A security alert was triggered by Discord in " + event.getGuild().getName());
            }
        }
    }
}
