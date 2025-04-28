package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.events.jobs.LinkCheckJob;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EnforceLinkChecks extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (Core.MODULES.get("safetyFeatures") && Core.SAFETY.getJSONObject("linkChecks").getBoolean("enabled")) {
            if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                return;
            }
            List<String> links = LinkCheckJob.getLinks(event.getMessage().getContentRaw());
            if (!links.isEmpty() && event.getMember() != null) {
                new LinkCheckJob(event, links);
            }
        }
    }
}
