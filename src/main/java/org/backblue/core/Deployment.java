package org.backblue.core;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.*;
import org.backblue.enums.Deployable;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class Deployment extends ListenerAdapter {

    private final JSONObject settings;
    private final List<CommandData> commands;

    @Override
    public void onGuildReady(@NonNull GuildReadyEvent event) {
        if (event.getGuild().getId().equals(settings.optString("_deploy", "")) || event.getGuild().getId().equals(settings.optString("_debug", ""))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }

    public Deployment(JSONObject settings, @NotNull List<Object> listeners) {
        this.settings = settings;
        commands = conversion(listeners);
    }

    private List<CommandData> conversion(List<Object> listeners) {
        if (listeners.isEmpty()) return List.of();
        List<CommandData> commands = new ArrayList<>();
        for (Object obj : listeners) {
            if (obj instanceof Deployable listener) {
                commands.addAll(listener.cmds());
            }
        }
        return commands;
    }

}
