package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.tasks.ProfileScanTask;
import org.backblue.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class Tasks extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("tasks")) {
            if ("stats".equals(event.getSubcommandName())) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("All Tasks: `" + String.format("%,d", Task.IDS) + "`");
                embedBuilder.setColor(0x00FF00);
                embedBuilder.setDescription("Tasks are reset per-restart.");
                embedBuilder.addField("BlueSkyRead", String.format("%,d", Task.Stats.bsky), true);
                embedBuilder.addField("ProfileScan", String.format("%,d", Task.Stats.profile), true);
                embedBuilder.addField("MessageScan", String.format("%,d", Task.Stats.message), true);
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
                    queue.append("- `").append(task.toString()).append("`\n");
                    i++;
                }
                List<Task> completedTasks = Bot.getBot().getCompletedTasks();
                for (int j = completedTasks.size() - 1; j >= 0 && i < 5; j--, i++) {
                    completed.append("- `").append(completedTasks.get(j).toString()).append("`\n");
                }

                if (queue.isEmpty()) {
                    embedBuilder.addField("In Queue", "None", false);
                } else {
                    embedBuilder.addField("In Queue:", String.valueOf(queue), false);
                }
                if (completed.isEmpty()) {
                    embedBuilder.addField("Completed", "None", false);
                } else {
                    embedBuilder.addField("Completed", String.valueOf(completed), false);
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
