package org.backblue.events;

import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class DisableDM extends ListenerAdapter {

    final Bot bot;

    public DisableDM(Bot bot) {
        this.bot = bot;
        bot.getScheduler().scheduleWithFixedDelay(this::check, 0, 1, TimeUnit.MINUTES);
    }

    private void check() {
        if (bot.isFeatureEnabled(FeatureFlag.DisableDMs)) {
            enable();
        } else {
            disable();
        }
    }

    private boolean isActive() {
        if (bot.getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil() == null) {
            return false;
        }
        return bot.getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil().toEpochSecond() > OffsetDateTime.now().toEpochSecond();
    }

    public void enable() {
        if (bot.isFeatureEnabled(FeatureFlag.DisableDMs) && bot.getDeploymentGuild() != null && !isActive()) {
            SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(bot.getDeploymentGuild().getSecurityIncidentActions().getInvitesDisabledUntil(), OffsetDateTime.now().plusSeconds(86399));
            bot.getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
        }
    }

    public void disable() {
        if (isActive()) {
            SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(bot.getDeploymentGuild().getSecurityIncidentActions().getInvitesDisabledUntil(), null);
            bot.getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
        }
    }

}
