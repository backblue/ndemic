package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class Tasks extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("tasks") && event.getSubcommandName() != null) {
            if (event.getSubcommandName().equals("skip")) {
                Task.getTasksQueue().remove();
            }
            if (event.getSubcommandName().equals("queue")) {
                EmbedBuilder raw = new EmbedBuilder()
                        .setColor(Color.CYAN)
                        .setTitle("Tasks Queue");
                if (Task.getTasksQueue().isEmpty()) {
                    raw.addField("Awaiting Tasks to Run", "None", false);
                } else {
                    int count = 0;
                    StringBuilder list = new StringBuilder();
                    for (Task task : Task.getTasksQueue()) {
                        if (count > 5) break;
                        list.append("**").append(count).append("**:").append(task).append("\n");
                        count++;
                    }
                    raw.addField("Awaiting Tasks to Run (" + Task.getTasksQueue().size() + " completed)", list.toString(), false);
                }
                if (Task.getTasksCompleted().isEmpty()) {
                    raw.addField("Completed Tasks", "None", false);
                } else {
                    int count = 0;
                    StringBuilder list = new StringBuilder();
                    for (Task task : Task.getTasksCompleted()) {
                        if (count > 5) break;
                        list.append("**").append(count).append("**:").append(task).append("\n");
                        count++;
                    }
                    raw.addField("Completed Tasks (" + Task.getTasksCompleted().size() + " completed)", list.toString(), false);
                }

                event.replyEmbeds(raw.build()).setEphemeral(true).queue();
            }
        }
    }
}
