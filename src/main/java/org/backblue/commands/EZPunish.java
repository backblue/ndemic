package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.*;

public class EZPunish extends ListenerAdapter {

    private static JSONArray RULEBOOK = null;
    private static StringSelectMenu VIOLATIONS_DROPDOWN = null;

    static {
        Path path = Path.of("data/rulebook.json");
        JSONObject fileContent = null;
        if (Files.exists(path)) {
            try {
                fileContent = new JSONObject(Files.readString(path));
                EZPunish.RULEBOOK = fileContent.getJSONArray("content");
                for (int i = 0; i < RULEBOOK.length(); i++) {
                    JSONObject rule = RULEBOOK.getJSONObject(i);
                    if (!rule.has("id") || !rule.has("title") || !rule.has("desc")) {
                        Bot.getBot().sendDebugMessage("autoMod", "Rulebook entry " + (i) + " is missing fields 'id', 'title', 'desc'. Disabling Rulebook and ezpunish");
                        EZPunish.RULEBOOK = null;
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        if (fileContent != null && fileContent.getJSONArray("keywords") != null) {
            StringSelectMenu.Builder builder = StringSelectMenu.create("ezpunish:violations");
            for (int i = 0; i < fileContent.getJSONArray("keywords").length(); i++) {
                JSONObject rule = fileContent.getJSONArray("keywords").getJSONObject(i);
                builder.addOption(rule.getString("title"), "ezpunish:"+rule.getString("modal"), rule.getString("desc"));
            }
            builder.setMinValues(1);
            builder.setMaxValues(4);
            VIOLATIONS_DROPDOWN = builder.build();
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if ("ezpunish".equals(event.getName())) {
            if (EZPunish.RULEBOOK == null) {
                event.reply(":x: The rulebook is not configured!").setEphemeral(true).queue();
                return;
            }
            EntitySelectMenu menu = EntitySelectMenu.create("ezpunish:target", EntitySelectMenu.SelectTarget.USER).build();
            StringSelectMenu punishment = StringSelectMenu.create("ezpunish:type")
                    .addOption("Softban", "softban", "Kicks the user and deletes an hour of messages")
                    .addOption("Ban", "ban", "Bans the user and deletes an hour of messages")
                    .setDefaultValues("softban")
                    .build();
            StringSelectMenu violations = StringSelectMenu.create("ezpunish:violations")
                    .addOption("Softban", "softban", "Kicks the user and deletes an hour of messages")
                    .addOption("Ban", "ban", "Bans the user and deletes an hour of messages")
                    .setMinValues(1)
                    .setMaxValues(4)
                    .build();
            TextInput note = TextInput.create("ezpunish:note", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Personalized note to the user regarding their punishment")
                    .setMinLength(1)
                    .setMaxLength(1000)
                    .setRequired(false)
                    .build();
            Modal modal = Modal.create("modal:ezpunish", "Moderation Action")
                    .addComponents(
                            Label.of("Target", menu),
                            Label.of("Punishment", punishment),
                            Label.of("Violations", VIOLATIONS_DROPDOWN),
                            Label.of("Attachments / Evidence", AttachmentUpload.of("ezpunish:evidence")),
                            Label.of("Notes", note)
                    )
                    .build();
            event.replyModal(modal).queue();
        }
    }

    public static JSONObject search(int i) {
        for (int j = 0; j < RULEBOOK.length(); j++) {
            JSONObject rule = RULEBOOK.getJSONObject(j);
            if (rule.getInt("id") == i) {
                return rule;
            }
        }
        return null;
    }

    public static MessageEmbed generatePunishEmbed(int ruleId, Member user, boolean ban) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (ban) {
            embedBuilder.setColor(Color.RED);
            embedBuilder.setTitle("Permanently Banned from " + user.getGuild().getName());
            embedBuilder.setFooter("You may not rejoin this guild.");
        } else {
            embedBuilder.setColor(Color.YELLOW);
            embedBuilder.setTitle("Removed from " + user.getGuild().getName());
            embedBuilder.setFooter("You may rejoin this guild in the future.");
        }
        embedBuilder.setDescription("You have been " + (ban ? "permanently banned" : "removed") + " from " + user.getGuild().getName() + " for violating the following rule below:");
        JSONObject rule = search(ruleId);
        embedBuilder.addField(rule.getString("title"), rule.getString("desc"), false);
        return embedBuilder.build();
    }

    public static void logToWarnings(int ruleId, Member user, boolean ban, String evidenceText, Message.Attachment evidenceImage, Member executor) {
        String status = ban ? "Banned" : "Kicked";
        if (evidenceImage == null) {
            Bot.getBot().sendDeploymentMessage("warn", user.getAsMention() + " - " + status + " - " + search(ruleId).getString("title") + "\nInitiated by: `" + executor.getUser().getName()+ "`\n" + evidenceText);
        } else {
            if (evidenceText == null) {
                evidenceText = "";
            }
            String finalEvidenceText = evidenceText;
            evidenceImage.getProxy().download().thenAccept(inputStream -> {
                FileUpload fileUpload = FileUpload.fromData(inputStream, evidenceImage.getFileName());
                Bot.getBot().sendDeploymentMessage("warn", user.getAsMention() + " - " + status + " - " + search(ruleId).getString("title") + "\nInitiated by: `" + executor.getUser().getName() + "`\n" + finalEvidenceText, fileUpload);
            });
        }
    }

    public record EZPunishResult(boolean success, String message) {}
}
