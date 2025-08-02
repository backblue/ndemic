package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.tasks.ProfileScanTask;
import org.backblue.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Tasks extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("tasks")) {
            if ("info".equals(event.getSubcommandName())) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("All Tasks: `" + String.format("%,d", Task.IDS) + "`");
                embedBuilder.setColor(0x00FF00);
                embedBuilder.setDescription("Tasks are reset per-restart.");
                embedBuilder.addField("BlueSkyRead", String.format("%,d", Task.Stats.bsky), false);
                embedBuilder.addField("ProfileScan", String.format("%,d", Task.Stats.profile), false);
                embedBuilder.addField("MessageScan", String.format("%,d", Task.Stats.message), false);
                embedBuilder.addField("cached_images", String.format("%,d", ProfileScanTask.hashSize()), false);

                event.replyEmbeds(embedBuilder.build()).queue();
            }
            if ("queue".equals(event.getSubcommandName())) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Task Queue");
                StringBuilder queue = new StringBuilder();
                StringBuilder completed = new StringBuilder();

                int i = 0;
                for (Task task : Bot.getBot().getTaskQueue()) {
                    if (i >= 5) {
                        break;
                    }
                    queue.append("- ").append(task.toString()).append("\n");
                    i++;
                }
                i = 0;
                for (Task task : Bot.getBot().getCompletedTasks()) {
                    if (i >= 5) {
                        break;
                    }
                    completed.append("- ").append(task.toString()).append("\n");
                    i++;
                }

                if (queue.isEmpty()) {
                    embedBuilder.addField("In Queue", "None", false);
                } else {
                    embedBuilder.addField("In Queue", queue.toString(), false);
                }
                if (completed.isEmpty()) {
                    embedBuilder.addField("Completed", "None", false);
                } else {
                    embedBuilder.addField("Completed", completed.toString(), false);
                }
                event.replyEmbeds(embedBuilder.build()).queue();
            }
            if ("lookup".equals(event.getSubcommandName())) {
                int id = Objects.requireNonNull(event.getOption("identifier")).getAsInt();
                EmbedBuilder embed = Bot.getBot().taskToEmbed(id);
                if (embed == null) {
                    event.reply(":x: No task found with ID **" + id + "**, Total IDs: `" + String.format("%,d", Task.IDS) + "`").setEphemeral(true).queue();
                    return;
                }
                event.replyEmbeds(embed.build()).queue();
            }
        }

    }
}
