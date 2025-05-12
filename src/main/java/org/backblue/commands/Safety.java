package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.events.jobs.Job;
import org.backblue.events.jobs.ProfileScanJob;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

public class Safety extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("safety")) {
            if (Core.MODULES.get("safetyFeatures")) {
                if (event.getSubcommandName().equals("status")) {

                }
                if (event.getSubcommandName().equals("lookup")) {
                    if (Job.ID_TO_JOB.isEmpty()) {
                        event.reply(":x: No jobs have been run yet.").setEphemeral(true).queue();
                        return;
                    }
                    int idToLookFor = event.getOption("identifier").getAsInt();
                    Job theJob = Job.search(idToLookFor);
                    if (theJob == null) {
                        int jobCounter = Job.getCounter() - 1;
                        event.reply(":x: Job ID not found! Job IDs are from `0 - " + jobCounter + "`.").setEphemeral(true).queue();
                        return;
                    }
                    HashMap<String, String> jobData = theJob.lookup();
                    EmbedBuilder raw;
                    raw = new EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("Job ID: `" + idToLookFor + "`");
                    for (String key : jobData.keySet()) {
                        if (jobData.get(key) == null) {
                            raw.addField(key, "N/A", true);
                        } else {
                            raw.addField(key, jobData.get(key), true);
                        }
                    }
                    double waited = (double) (Long.parseLong(jobData.get("started")) - Long.parseLong(jobData.get("created"))) / 1000;
                    double completed = (double) (Long.parseLong(jobData.get("completed")) - Long.parseLong(jobData.get("started"))) / 1000;
                    raw.setFooter("Waited to Run: " + waited + "s | Time Elapsed: " + completed + "s");
                    MessageEmbed msg = raw.build();
                    event.replyEmbeds(msg).setEphemeral(true).queue();
                }
                if (event.getSubcommandName().equals("dequeue")) {
                    if (ProfileScanJob.QUEUE.isEmpty()) {
                        event.reply(":x: No jobs in queue!").setEphemeral(true).queue();
                        return;
                    }
                    event.reply(":white_check_mark: Forced run of a job. (on this thread)").setEphemeral(true).queue();
                    ProfileScanJob.QUEUE.peek().process();
                }
                if (event.getSubcommandName().equals("skip")) {
                    ProfileScanJob.QUEUE.remove();
                    event.reply(":white_check_mark: Skipped the next job in the queue (if it does exist).").setEphemeral(true).queue();
                }
                if (event.getSubcommandName().equals("queue")) {
                    EmbedBuilder raw = new EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("Jobs Queue since <t:" + (Instant.now().getEpochSecond()) + ":f>");
                    if (ProfileScanJob.QUEUE.isEmpty()) {
                        raw.addField("Awaiting Jobs to Run", "None", false);
                    } else {
                        StringBuilder content = new StringBuilder();
                        int count = 0;
                        for (Job job : ProfileScanJob.QUEUE) {
                            if (count > 5) {
                                break;
                            }
                            content.append(job).append("\n");
                            count++;
                        }
                        raw.addField("Awaiting Jobs to Run - " + ProfileScanJob.QUEUE.size() + " jobs", content.toString(), false);
                    }
                    if (ProfileScanJob.RECENT_COMPLETE_JOBS.isEmpty()) {
                        raw.addField("Recently Completed", "None", false);
                    } else {
                        StringBuilder content = new StringBuilder();
                        ArrayList<Job> temp = new ArrayList<>(ProfileScanJob.RECENT_COMPLETE_JOBS);
                        int start = Math.max(0, temp.size() - 5);
                        for (int i = temp.size()-1; i >= start; i--) {
                            content.append(temp.get(i)).append("\n");
                        }
                        raw.addField("Recently Completed - " + ProfileScanJob.RECENT_COMPLETE_JOBS.size() + " jobs", content.toString(), false);
                    }
                    raw.setFooter("'!' means that the job was invalid");
                    MessageEmbed message = raw.build();
                    event.replyEmbeds(message).setEphemeral(true).queue();
                }
            } else {
                event.reply(":x: **safetyFeatures** module must be enabled first!").setEphemeral(true).queue();
            }
        }
    }
}
