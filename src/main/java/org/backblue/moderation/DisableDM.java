package org.backblue.moderation;

import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.enums.FeatureFlag;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public final class DisableDM extends ListenerAdapter {

    final Bot bot;

    public DisableDM(Bot bot) {
        this.bot = bot;
        bot.getScheduler().scheduleAtFixedRate(this::check, 0, 1, TimeUnit.MINUTES);
    }

    private void check() {
        if (bot.isFeatureEnabled(FeatureFlag.DisableDMs)) {
            enable();
        } else {
            disable();
        }
    }

    private OffsetDateTime deploymentGuildInvitesDisabledUntil() {
        OffsetDateTime until = bot.getDeploymentGuild().getSecurityIncidentActions().getInvitesDisabledUntil();
        if (until == null) return null;
        return OffsetDateTime.now().isAfter(until) ? OffsetDateTime.now().plusSeconds(1) : until;
    }

    private boolean isActive() {
        if (bot.getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil() == null) return false;
        return bot.getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil().toEpochSecond() > OffsetDateTime.now().toEpochSecond();
    }

    private void enable() {
        try {
            if (bot.isFeatureEnabled(FeatureFlag.DisableDMs) && bot.getDeploymentGuild() != null && !isActive()) {
                SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(this.deploymentGuildInvitesDisabledUntil(), OffsetDateTime.now().plusSeconds(86399));
                bot.getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
            }
        } catch (NullPointerException ignored) {}

    }

    private void disable() {
        if (isActive()) {
            SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(this.deploymentGuildInvitesDisabledUntil(), null);
            bot.getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
        }
    }

}
