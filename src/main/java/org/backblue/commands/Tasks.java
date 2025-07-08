package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Tasks extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("tasks")) {
            if ("lookup".equals(event.getSubcommandName())) {
                int id = Objects.requireNonNull(event.getOption("identifier")).getAsInt();
                EmbedBuilder embed = Bot.getBot().taskToEmbed(id);
                if (embed == null) {
                    event.reply(":x: No task found with ID **" + id + "**, Total IDs: `" + Task.IDS + "`").setEphemeral(true).queue();
                    return;
                }
                event.replyEmbeds(embed.build()).queue();
            }
        }

    }
}
