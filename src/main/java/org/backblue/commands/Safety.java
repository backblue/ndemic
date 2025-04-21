package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.self.SelfUpdateAvatarEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.libraries.Job;
import org.backblue.libraries.ProfileScanJob;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.HashMap;

public class Safety extends ListenerAdapter {
    @Override
    public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {
        if (Core.MODULES.get("safetyFeatures")) {
            if (event.getUser().getAvatarUrl() == null) {
                return;
            }
            if (Core.SAFETY.getJSONObject("scanProfile").getBoolean("onGuildAvatarChange")) {
                new ProfileScanJob(event.getUser().getId(), "updatedAvatarGuild");
            }
        }
    }

    @Override
    public void onUserUpdateAvatar(@NotNull UserUpdateAvatarEvent event) {
        if (Core.MODULES.get("safetyFeatures")) {
            if (event.getUser().getAvatarUrl() == null) {
                return;
            }
            if (Core.SAFETY.getJSONObject("scanProfile").getBoolean("onUserAvatarChange")) {
                new ProfileScanJob(event.getUser().getId(), "updatedAvatarUser");
            }
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (Core.MODULES.get("safetyFeatures")) {
            if (event.getUser().getAvatarUrl() == null) {
                return;
            }
            if (Core.SAFETY.getJSONObject("scanProfile").getBoolean("onJoin")) {
                new ProfileScanJob(event.getUser().getId(), "join");
            }
        }
    }


    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("safetylookup")) {
            if (Core.MODULES.get("safetyFeatures")) {
                int idToLookFor = event.getOption("identifier").getAsInt();
                Job theJob = Job.search(idToLookFor);
                if (theJob == null) {
                    event.reply(":x: Job ID not found! Job IDs are from `0 - " + Job.getCounter() + "`.\nOr, no jobs have been run yet.").setEphemeral(true).queue();
                    return;
                }
                HashMap<String, String> jobData = theJob.lookup();
                EmbedBuilder raw = new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Job ID: `" + idToLookFor + "`");
                for (String key : jobData.keySet()) {
                    if (jobData.get(key) == null) {
                        raw.addField(key, "N/A", true);
                    } else {
                        raw.addField(key, jobData.get(key), true);
                    }
                }
                MessageEmbed msg = raw.build();
                event.replyEmbeds(msg).setEphemeral(false).queue();

            } else {
                event.reply(":x: **safetyFeatures** module must be enabled first!").setEphemeral(true).queue();
            }
        }
        if (event.getName().equals("safety")) {
            if (Core.MODULES.get("safetyFeatures")) {
                if (event.getSubcommandName().equals("dequeue")) {
                    if (ProfileScanJob.QUEUE.isEmpty()) {
                        event.reply(":x: No jobs in queue!").setEphemeral(true).queue();
                        return;
                    }
                    event.reply(":white_check_mark: Forced run of a job.").setEphemeral(true).queue();
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
                        for (Job job : ProfileScanJob.QUEUE) {
                            content.append(job).append("\n");
                        }
                        raw.addField("Awaiting Jobs to Run", content.toString(), false);
                    }
                    if (ProfileScanJob.RECENT_COMPLETE_JOBS.isEmpty()) {
                        raw.addField("Recently Completed", "None", false);
                    } else {
                        StringBuilder content = new StringBuilder();
                        for (Job job : ProfileScanJob.QUEUE) {
                            content.append(job).append("\n");
                        }
                        raw.addField("Recently Completed", content.toString(), false);
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
