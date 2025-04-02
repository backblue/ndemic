package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.libraries.Job;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
public class Safety extends ListenerAdapter {

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (Core.MODULES.get("safetyFeatures")) {
            new Job(event.getUser().getId(), event.getUser().getAvatarUrl(), "profileScan");
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("safety")) {
            if (Core.MODULES.get("safetyFeatures")) {
                if (event.getSubcommandName().equals("enqueue")) {
                    String name = event.getOption("name").getAsString();
                    String desc = event.getOption("desc").getAsString();
                    String type = event.getOption("type").getAsString();
                    Job job = new Job(name, desc, type);
                    event.reply(":white_check_mark: Job **" + job + "** added to queue.").setEphemeral(true).queue();
                }
                if (event.getSubcommandName().equals("dequeue")) {
                    if (Job.QUEUE.isEmpty()) {
                        event.reply(":x: No jobs in queue!").setEphemeral(true).queue();
                        return;
                    }
                    event.reply(":white_check_mark: Forced run of a job.").setEphemeral(true).queue();
                    Job.process();
                }
                if (event.getSubcommandName().equals("queue")) {
                    EmbedBuilder raw = new EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("Jobs Queue since <t:" + (Instant.now().getEpochSecond()) + ":f>");
                    if (Job.QUEUE.isEmpty()) {
                        raw.addField("Awaiting Jobs to Run (10 max)", "None", false);
                    } else {
                        int i = 0;
                        String content = "";
                        for (Job job : Job.QUEUE) {
                            content = content + i + ". " + job + "\n";
                            i++;
                            if (i > 10) {
                                break;
                            }
                        }
                        raw.addField("Awaiting Jobs to Run (10 shown)", content, false);
                    }
                    if (Job.RECENT_COMPLETE_JOBS.isEmpty()) {
                        raw.addField("Recently Completed (5 max)", "None", false);
                    } else {
                        String content = "";
                        for (int i = Job.RECENT_COMPLETE_JOBS.size() - 1; i >= 0; i--) {
                            content = content + Job.RECENT_COMPLETE_JOBS.get(i) + "\n";
                        }
                        raw.addField("Recently Completed (5 max)", content, false);
                    }
                    raw.setFooter("'!' means that the job was invalid");
                    MessageEmbed message = raw.build();
                    event.replyEmbeds(message).queue();
                }
            } else {
                event.reply(":x: **safetyFeatures** module must be enabled first!").setEphemeral(true).queue();
            }
        }
    }
}
