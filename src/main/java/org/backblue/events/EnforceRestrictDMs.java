package org.backblue.events;

import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import org.backblue.Bot;

import java.time.OffsetDateTime;

public class EnforceRestrictDMs {

    public EnforceRestrictDMs() {
        Bot.getBot().getScheduler().scheduleWithFixedDelay(this::check, 0, 1, java.util.concurrent.TimeUnit.MINUTES);
    }

    public boolean enabled() {
        return Bot.getBot().getModuleValue("restrictDMs");
    }

    private boolean isActive() {
        if (Bot.getBot().getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil() == null) {
            return false;
        }
        return Bot.getBot().getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil().toEpochSecond() > OffsetDateTime.now().toEpochSecond();
    }

    public void check() {
        if (enabled() && Bot.getBot().getDeploymentGuild() != null && !isActive()) {
            SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(Bot.getBot().getDeploymentGuild().getSecurityIncidentActions().getInvitesDisabledUntil(), OffsetDateTime.now().plusMinutes(1438));
            Bot.getBot().getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
        }
    }

    public void disable() {
        if (isActive()) {
            SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(Bot.getBot().getDeploymentGuild().getSecurityIncidentActions().getInvitesDisabledUntil(), null);
            Bot.getBot().getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
        }
    }
}
