package org.backblue.events;

import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateSecurityIncidentDetectionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.FeatureFlag;
import org.jspecify.annotations.NonNull;

import java.time.OffsetDateTime;

public class RaidProtect extends ListenerAdapter {
    private OffsetDateTime lastKnownRaidAlert = OffsetDateTime.MIN;
    final Bot bot;

    public RaidProtect(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onGuildUpdateSecurityIncidentDetections(@NonNull GuildUpdateSecurityIncidentDetectionsEvent event) {
        if (bot.getDeploymentGuild().getId().equals(event.getGuild().getId())) {
            if (event.getNewSecurityIncidentDetections() == event.getOldSecurityIncidentDetections()) {
                return;
            }
            OffsetDateTime raid = event.getNewSecurityIncidentDetections().getTimeDetectedRaid();
            if (raid != null && raid.isAfter(lastKnownRaidAlert)) {
                lastKnownRaidAlert = raid;
                if (bot.isFeatureEnabled(FeatureFlag.RaidPauseInvites)) {
                    SecurityIncidentActions actions = event.getGuild().getSecurityIncidentActions();
                    if (actions.getInvitesDisabledUntil() == null) {
                        actions = SecurityIncidentActions.enabled(OffsetDateTime.now().plusHours(4), bot.getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil());
                        bot.getDeploymentGuild().modifySecurityIncidents(actions).queue();
                    }
                }
                if (bot.isFeatureEnabled(FeatureFlag.AutoModAlerts)) {
                    bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getAsMention());
                    bot.getIO().send(DefinedChannel.DebugAutoModAlert, "A security alert was triggered by Discord in " + event.getGuild().getName());
                }
            }

        }
    }
}
