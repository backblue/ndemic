package org.backblue.events;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.guild.SecurityIncidentActions;
import org.backblue.Bot;

import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class EnforceSecurityActions {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public EnforceSecurityActions() {
        schedule();
    }

    public void schedule() {
        check();
        scheduler.scheduleWithFixedDelay(this::check, 1, 1, java.util.concurrent.TimeUnit.MINUTES);
    }

    private boolean isEnabled() {
        return Bot.getBot().getModuleValue("restrictDMs");
    }

    private Guild getDeploymentGuild() {
        return Bot.getBot().getJDA().getGuildById(Bot.getBot().getDeployment().get("guild"));
    }

    private boolean isActive() {
        return getDeploymentGuild().getSecurityIncidentActions().getDirectMessagesDisabledUntil() != null;
    }

    public void check() {
        if (isEnabled() && getDeploymentGuild() != null && !isActive()) {
            SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(getDeploymentGuild().getSecurityIncidentActions().getInvitesDisabledUntil(), OffsetDateTime.now().plusMinutes(1438));
            getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
        }
    }

    public void disable() {
        if (isActive()) {
            SecurityIncidentActions incidentActions = SecurityIncidentActions.enabled(getDeploymentGuild().getSecurityIncidentActions().getInvitesDisabledUntil(), null);
            getDeploymentGuild().modifySecurityIncidents(incidentActions).queue();
        }
    }
}
