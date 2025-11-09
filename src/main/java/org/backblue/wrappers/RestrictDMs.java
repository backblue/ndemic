package org.backblue.wrappers;

import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import org.backblue.Bot;
import org.backblue.utilities.NdemicModule;

import java.time.OffsetDateTime;

public class RestrictDMs implements NdemicModule, NdemicModule.ToggleActions {

    public RestrictDMs() {
        Bot.getBot().getScheduler().scheduleWithFixedDelay(this::enable, 0, 1, java.util.concurrent.TimeUnit.MINUTES);
    }

    @Override
    public String name() {
        return "restrictDMs";
    }

    private boolean isActive() {
        if (Bot.getBot().getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil() == null) {
            return false;
        }
        return Bot.getBot().getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil().toEpochSecond() > OffsetDateTime.now().toEpochSecond();
    }

    public void enable() {
        if (isEnabled() && Bot.getBot().getDeploymentGuild() != null && !isActive()) {
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
