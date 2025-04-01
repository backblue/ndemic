package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.jetbrains.annotations.NotNull;

public class EnforceFanRole extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (Core.MODULES.get("enforceFanRole")) {
            if (event.getMember() == null) {
                return;
            }

            if (event.getMember().getUser().isBot()) {
                return;
            }

            if (event.getMember().hasPermission(Permission.ADMINISTRATOR) || event.getAuthor().isBot()) {
                return;
            }
            if (event.getGuild().getId().equals(Core.DEPLOYMENT.get("guild"))) {
                for (Role role : event.getMember().getRoles()) {
                    if (role.getName().contains("Fan")) {
                        return;
                    }
                }

                try {
                    event.getMessage().delete().queue();
                } catch (Exception e) {}
                event.getMember().getUser().openPrivateChannel()
                        .queue(channel -> channel.sendMessage(
                                        "Hello,\n\nYou must have either `@Plague Inc. Fan`, `@Rebel Inc. Fan` or `@After Inc. Fan` role to be able to chat in **Ndemic Creations**.\n\n[You may pick up a role here:](https://discord.com/channels/523349543505362945/690604818661638144/690620288622133269) <#690604818661638144>"
                                ).queue(),
                                error -> {
                                });
                if (Core.MODULES.get("analytics")) {
                    TextChannel channel = event.getJDA().getTextChannelById(Core.ANALYTICS.get("enforcement"));
                    channel.sendMessage("Prevented user " + event.getMember().getAsMention() + " from chatting in <#" + event.getChannel().getId() + "> due to having no 'Fan' roles.").queue();
                }
            }
        }
    }
}
