package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
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
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class EZPunish extends ListenerAdapter {

    private static HashMap<Integer, JSONObject> RULEBOOK = new HashMap<>();
    private static final HashMap<String, JSONObject> MODAL_TO_RULEBOOK = new HashMap<>();
    private static StringSelectMenu VIOLATIONS_DROPDOWN = null;
    private static boolean ENABLED = true;

    static {
        Path path = Path.of("data/rulebook.json");
        JSONObject fileContent = null;
        if (Files.exists(path)) {
            try {
                fileContent = new JSONObject(Files.readString(path));
                JSONArray apple = fileContent.getJSONArray("content");
                for (int i = 0; i < apple.length(); i++) {
                    JSONObject rule = apple.getJSONObject(i);
                    if (!rule.has("id") || !rule.has("title") || !rule.has("desc")) {
                        Bot.getBot().sendDebugMessage("autoMod", "Rulebook entry " + (i) + " is missing fields 'id', 'title', 'desc'. Disabling Rulebook and ezpunish");
                        EZPunish.RULEBOOK = null;
                        ENABLED = false;
                        break;
                    }
                    if (RULEBOOK.containsKey(rule.getInt("id"))) {
                        Bot.getBot().sendDeploymentMessage("automod" , "Rulebook entry " + (i) + " has a duplicate ID of " + rule.getInt("id") + ". Ignoring duplicate.");
                    } else {
                        RULEBOOK.put(rule.getInt("id"), rule);
                    }
                }
                for (int i = 0; i < fileContent.getJSONArray("keywords").length(); i++) {
                    JSONObject rule = fileContent.getJSONArray("keywords").getJSONObject(i);
                    MODAL_TO_RULEBOOK.put("ezpunish:"+rule.getString("modal"), rule);
                }
            } catch (IOException ignored) {
            }
        }
        if (fileContent != null && fileContent.getJSONArray("keywords") != null && ENABLED) {
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
            RadioGroup punishment = RadioGroup.create("ezpunish:type")
                    .addOption("Softban (+1hr removal of messages)", "softban")
                    .addOption("Ban (+1hr removal of messages)", "ban")
                    .build();
            TextInput note = TextInput.create("ezpunish:note", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Personalized note to the user regarding their punishment")
                    .setMinLength(0)
                    .setMaxLength(1000)
                    .setRequired(false)
                    .build();
            Modal modal = Modal.create("modal:ezpunish", "Moderation Action")
                    .addComponents(
                            Label.of("Target", menu),
                            Label.of("Punishment", punishment),
                            Label.of("Violations", VIOLATIONS_DROPDOWN),
                            Label.of("Attachments / Evidence", AttachmentUpload.create("ezpunish:evidence").setRequired(false).build()),
                            Label.of("Notes", note)
                    )
                    .build();
            event.replyModal(modal).queue();
        }
    }

    public static JSONObject search(int i) {
        return RULEBOOK.get(i);
    }

    public static boolean enabled() {
        return ENABLED;
    }
    public static StringSelectMenu getViolationsDropdown() {
        return EZPunish.VIOLATIONS_DROPDOWN;
    }

    public static MessageEmbed generatePunishEmbed(List<String> violations, Member user, boolean ban, String notes, String url) {
        EmbedBuilder embedBuilder = getEmbedBuilder(user, ban);
        embedBuilder.setThumbnail(url);
        SortedMap<Integer, String> ruleViolationsMap = new TreeMap<>();
        for (String violation : violations) {
            JSONObject violationRule = EZPunish.MODAL_TO_RULEBOOK.get(violation);
            if (violationRule != null) {
                String point = violationRule.getString("desc");
                if (ruleViolationsMap.containsKey(violationRule.getInt("id"))) {
                    ruleViolationsMap.put(violationRule.getInt("id"), ruleViolationsMap.get(violationRule.getInt("id")) + "\n- **" + point + "**");
                } else {
                    ruleViolationsMap.put(violationRule.getInt("id"), EZPunish.RULEBOOK.get(violationRule.getInt("id")).getString("desc") + " Specifically:\n- **" + point + "**");
                }
            }
        }
        for (Integer ruleId : ruleViolationsMap.keySet()) {
            embedBuilder.addField(search(ruleId).getString("title"), ruleViolationsMap.get(ruleId), false);
        }
        if (notes != null) {
            embedBuilder.addField("Moderator's Note", notes, false);
        }
        return embedBuilder.build();
    }

    private static @NotNull EmbedBuilder getEmbedBuilder(Member user, boolean ban) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (ban) {
            embedBuilder.setColor(Color.RED);
            embedBuilder.setTitle("Permanently Banned from " + user.getGuild().getName());
            embedBuilder.setFooter("This ban is indefinite.");
        } else {
            embedBuilder.setColor(Color.YELLOW);
            embedBuilder.setTitle("Removed from " + user.getGuild().getName());
            embedBuilder.setFooter("Follow the rules next time to avoid future removals.");
        }
        embedBuilder.setDescription("You have been " + (ban ? "permanently banned" : "removed") + " from **" + user.getGuild().getName() + "** for violating the following rule(s) below:");
        return embedBuilder;
    }

    public static void logToWarnings(List<String> violations, Member user, boolean ban, String evidenceText, List<Message.Attachment> evidenceImages, Member executor) {
        String status = ban ? "Banned" : "Kicked";
        String reason = EZPunish.MODAL_TO_RULEBOOK.get(violations.getFirst()).getString("title");
        if (reason == null || reason.isEmpty()) {
            reason = "Moderator Action";
        }
        if (evidenceImages == null || evidenceImages.isEmpty()) {
            Bot.getBot().sendDeploymentMessage("warn", user.getAsMention() + " - " + status + " - " + reason + "\nInitiated by: `" + executor.getUser().getName()+ "`\n" + evidenceText);
        } else {
            if (evidenceText == null) {
                evidenceText = "";
            }
            String finalEvidenceText = evidenceText;
            String finalReason = reason;

            List<CompletableFuture<FileUpload>> futures = evidenceImages.stream()
                    .map(attachment -> attachment.getProxy().download()
                            .thenApply(inputStream -> FileUpload.fromData(inputStream, attachment.getFileName())))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenAccept(v -> {
                        FileUpload[] uploads = futures.stream()
                                .map(CompletableFuture::join)
                                .toArray(FileUpload[]::new);
                        Bot.getBot().sendDeploymentMessage("warn", user.getAsMention() + " - " + status + " - " + finalReason + "\nInitiated by: `" + executor.getUser().getName() + "`\n" + finalEvidenceText, uploads);
                    });
        }
    }

    public record EZPunishResult(boolean success, String message) {}
}
