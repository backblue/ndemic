package org.backblue.commands;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;

public class Module extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("module")) {
            String module = event.getOption("module").getAsString();
            Boolean enabled = event.getOption("enabled").getAsBoolean();

            if (module.equals("info")) {
                if (enabled) {
                    event.reply(Core.SETTINGS.toString()).setEphemeral(true).queue();
                    return;
                } else {
                    event.reply(Core.MODULES.toString()).setEphemeral(true).queue();
                    return;
                }
            } else if (!Core.MODULES.containsKey(module)) {
                event.reply(":x: Module **" + module + "** does not exist!").setEphemeral(true).queue();
                return;
            }

            for (int i = 0; i < Core.SETTINGS.getJSONArray("modules").length(); i++) {
                if (Core.SETTINGS.getJSONArray("modules").getJSONObject(i).getString("name").equals(module)) {
                    Core.SETTINGS.getJSONArray("modules").getJSONObject(i).put("enabled", enabled);
                    break;
                }
            }

            Core.MODULES.put(module, enabled);

            try {
                FileWriter file = new FileWriter("data/settings.json");
                file.write(Core.SETTINGS.toString());
                file.close();
            } catch (IOException e) {
                event.reply(":x: Failed to change setting!").setEphemeral(true).queue();
                return;
            }

            Core.MODULES.put(module, enabled);
            event.reply(":white_check_mark: Module **" + module + "** is " + (enabled ? "**enabled**." : "**disabled**.")).queue();
            Core.loadModules();
            if (Core.MODULES.get("analytics")) {
                TextChannel analysis = event.getJDA().getTextChannelById(Core.ANALYTICS.get("autoMod"));
                analysis.sendMessage(event.getUser().getName() + " changed setting " + module + " to " + enabled).queue();
            }
        }
    }
}
