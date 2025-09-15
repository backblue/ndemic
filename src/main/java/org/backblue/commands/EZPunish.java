package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.io.*;
import java.util.concurrent.TimeUnit;

public class EZPunish extends ListenerAdapter {

    private static JSONArray RULEBOOK = null;

    static {
        Path path = Path.of("data/rulebook.json");
        if (Files.exists(path)) {
            JSONObject fileContent;
            try {
                fileContent = new JSONObject(Files.readString(path));
                EZPunish.RULEBOOK = fileContent.getJSONArray("content");
                for (int i = 0; i < RULEBOOK.length(); i++) {
                    JSONObject rule = RULEBOOK.getJSONObject(i);
                    if (!rule.has("id") || !rule.has("title") || !rule.has("desc")) {
                        Bot.getBot().sendDebugMessage("autoMod", "Rulebook entry " + (i) + " is missing fields 'id', 'title', 'desc'. Disabling Rulebook and ezpunish");
                        EZPunish.RULEBOOK = null;
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if ("ezpunish".equals(event.getName())) {
            if (event.getGuild() == null) {
                event.reply("This command can only be used in a server").setEphemeral(true).queue();
                return;
            }
            if (EZPunish.RULEBOOK == null) {
                event.reply("The rulebook is not loaded, cannot use this feature").setEphemeral(true).queue();
                return;
            }
            @NotNull User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
            Member member = event.getGuild().getMember(user);
            boolean ban = Objects.requireNonNull(event.getOption("ban")).getAsBoolean();
            int ruleId = Objects.requireNonNull(event.getOption("ruleid")).getAsInt();
            String evidenceText = null;
            if (event.getOption("evidencetext") != null) {
                evidenceText = Objects.requireNonNull(event.getOption("evidencetext")).getAsString();
            }
            Message.Attachment evidenceImage = null;
            if (event.getOption("evidenceimage") != null) {
                evidenceImage = Objects.requireNonNull(event.getOption("evidenceimage")).getAsAttachment();
            }
            if (evidenceText == null && evidenceImage == null) {
                event.reply("You must provide at least one form of evidence, either text or image.").setEphemeral(true).queue();
                return;
            }
            if (search(ruleId) == null) {
                event.reply("The rule ID provided does not exist in the rulebook.").setEphemeral(true).queue();
                return;
            }
            if (member != null) {
                if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR) || Objects.requireNonNull(member).hasPermission(Permission.ADMINISTRATOR)) {
                    event.reply(":x: Cannot perform this action. Check logs").setEphemeral(true).queue();
                    return;
                }
                Bot.getBot().sendUserMessage(user, generatePunishEmbed(ruleId, user, ban, event));
                logToWarnings(ruleId, user, ban, evidenceText, evidenceImage, event);
                member.ban(1, TimeUnit.HOURS).reason("Moderator initiated, rule " + ruleId + " (" + event.getUser().getName() + ")").queue();
                event.reply(":white_check_mark: User has been " + (ban ? "banned" : "removed") + " and logged.").queue();
                Bot.getBot().getScheduler().schedule(() -> {
                    if (!ban) {
                        event.getGuild().unban(member).queue();
                    }
                }, 4, TimeUnit.SECONDS);
            } else {
                event.reply("The user is not in the server, cannot remove.").setEphemeral(true).queue();
            }
        }
    }

    private static @Nullable JSONObject search(int i) {
        for (int j = 0; j < RULEBOOK.length(); j++) {
            JSONObject rule = RULEBOOK.getJSONObject(j);
            if (rule.getInt("id") == i) {
                return rule;
            }
        }
        return null;
    }

    private MessageEmbed generatePunishEmbed(int ruleId, User user, boolean ban, SlashCommandInteractionEvent e) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (ban) {
            embedBuilder.setColor(Color.RED);
            embedBuilder.setTitle("Permanently Banned from " + Objects.requireNonNull(e.getGuild()).getName());
            embedBuilder.setFooter("You may not rejoin this guild.");
        } else {
            embedBuilder.setColor(Color.YELLOW);
            embedBuilder.setTitle("Removed from " + Objects.requireNonNull(e.getGuild()).getName());
            embedBuilder.setFooter("You may rejoin this guild in the future.");
        }
        embedBuilder.setDescription("You have been " + (ban ? "permanently banned" : "removed") + " from " + e.getGuild().getName() + " for violating the following rule below:");
        JSONObject rule = search(ruleId);
        embedBuilder.addField(rule.getString("title"), rule.getString("desc"), false);
        return embedBuilder.build();
    }
    private void logToWarnings(int ruleId, User user, boolean ban, String evidenceText, Message.Attachment evidenceImage, SlashCommandInteractionEvent e) {
        String status = ban ? "Banned" : "Kicked";
        if (evidenceImage == null) {
            Bot.getBot().sendDeploymentMessage("warn", user.getAsMention() + " - " + status + " - " + search(ruleId).getString("title") + "\nInitiated by: `" + e.getUser().getName()+ "`\n" + evidenceText);
        } else {
            if (evidenceText == null) {
                evidenceText = "";
            }
            String finalEvidenceText = evidenceText;
            evidenceImage.getProxy().download().thenAccept(inputStream -> {
                FileUpload fileUpload = FileUpload.fromData(inputStream, evidenceImage.getFileName());
                Bot.getBot().sendDeploymentMessage("warn", user.getAsMention() + " - " + status + " - " + search(ruleId).getString("title") + "\nInitiated by: `" + e.getUser().getName() + "`\n" + finalEvidenceText, fileUpload);
            });
        }
    }
}
