package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;

public class Module extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("module")) {
            String module = Objects.requireNonNull(event.getOption("module")).getAsString();
            Boolean enabled = Objects.requireNonNull(event.getOption("enabled")).getAsBoolean();

            if (module.equals("info")) {
                event.reply(Bot.getBot().getModules().toString()).setEphemeral(true).queue();
                return;
            } else if (!Bot.getBot().getModules().has(module)) {
                event.reply(":x: Module **" + module + "** does not exist!").setEphemeral(true).queue();
                return;
            }

            JSONObject moduleData = new JSONObject();
            moduleData.put("description", Bot.getBot().getModuleDescription(module));
            moduleData.put("enabled", enabled);

            Bot.getBot().getModules().remove(module);
            Bot.getBot().getModules().put(module, moduleData);

            try {
                FileWriter file = new FileWriter("data/modules.json");
                file.write(Bot.getBot().getModules().toString());
                file.close();
            } catch (IOException e) {
                event.reply(":x: Failed to change setting!").setEphemeral(true).queue();
                return;
            }

            Bot.getBot().getModules().remove(module);
            Bot.getBot().getModules().put(module, moduleData);

            if (module.equals("restrictDMs") && enabled) {
                Bot.getBot().getDiscordSecurityIncidentActions().check();
            } else if (module.equals("restrictDMs")) {
                Bot.getBot().getDiscordSecurityIncidentActions().disable();
            }

            event.reply(":white_check_mark: Module **" + module + "** is " + (enabled ? "**enabled**." : "**disabled**.")).queue();
            Bot.getBot().sendDebugMessage("autoMod", event.getUser().getName() + " changed setting " + module + " to " + enabled);
        }
    }
}