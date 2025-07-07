package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
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
            if ("queue".equals(event.getSubcommandName())) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Task Queue");
                if (Bot.getBot().getTaskQueue().isEmpty()) {
                    embed.addField("Waiting", "None", false);
                } else {
                    StringBuilder waiting = new StringBuilder();
                    for (Task task : Bot.getBot().getTaskQueue()) {
                        waiting.append("`").append(task).append("`").append("\n");
                    }
                    embed.addField("Waiting", waiting.toString(), false);
                }
                if (Bot.getBot().getCompletedTasks().isEmpty()) {
                    embed.addField("Completed", "None", false);
                } else {
                    StringBuilder waiting = new StringBuilder();
                    for (Task task : Bot.getBot().getTaskQueue()) {
                        waiting.append("`").append(task).append("`").append("\n");
                    }
                    embed.addField("Completed", waiting.toString(), false);
                }
                embed.setColor(Color.YELLOW);
                event.replyEmbeds(embed.build()).queue();
            }
            if ("scan".equals(event.getSubcommandName())) {
                User user = Objects.requireNonNull(event.getOption("user")).getAsUser();

            }
        }

    }
}
