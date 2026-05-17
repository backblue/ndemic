package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
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
import org.backblue.core.Bot;
import org.backblue.core.IO;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class EZPunish extends ListenerAdapter {

    Bot bot;
    private HashMap<Integer, JSONObject> rulebook = new HashMap<>();
    private final HashMap<String, JSONObject> modalToRuleBook = new HashMap<>();
    private StringSelectMenu violationsDropdown = null;

    public EZPunish(Bot bot, JSONObject json) {
        this.bot = bot;
        buildRulebook(json);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if ("ezpunish".equals(event.getName())) {
            if (this.rulebook == null) {
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
                            Label.of("Violations", violationsDropdown),
                            Label.of("Attachments / Evidence", AttachmentUpload.create("ezpunish:evidence").setRequired(false).build()),
                            Label.of("Notes", note)
                    )
                    .build();
            event.replyModal(modal).queue();
        }
    }

    public String ezPunish(Member target, Member executor, List<String> violations, boolean ban, String evidenceText, List<Message.Attachment> evidenceImages, String notes) {
        if (target == null || executor == null) {
            return "Invalid targets provided.";
        }
        if (!executor.hasPermission(Permission.BAN_MEMBERS)) {
            return "Executor lacks permissions.";
        }
        if (evidenceText == null && evidenceImages == null) {
            return "No evidence provided.";
        }
        if (target.hasPermission(Permission.BAN_MEMBERS)) {
            return"Target has Administrator permissions.";
        }
        bot.getIO().send(target.getUser(), "", generatePunishEmbed(violations, target, ban, notes, target.getGuild().getIconUrl()));
        logToWarnings(violations, target, ban, evidenceText, evidenceImages, executor);
        target.ban(1, TimeUnit.HOURS).reason("Moderator initiated by " + executor.getUser().getName()).queue();
        bot.scheduler.schedule(() -> {
            if (!ban) {
                target.getGuild().unban(target).queue();
            }
        }, 4, TimeUnit.SECONDS);
        return "User has been " + (ban ? "banned" : "removed") + " and logged.";
    }

    public JSONObject search(int i) {
        return this.rulebook.get(i);
    }

    private void logToWarnings(List<String> violations, Member target, boolean ban, String evidenceText, List<Message.Attachment> evidenceImages, Member executor) {
        String status = ban ? "Banned" : "Kicked";
        String reason = this.modalToRuleBook.get(violations.getFirst()).getString("title");
        if (reason == null || reason.isEmpty()) {
            reason = "Moderator Action";
        }
        if (evidenceImages == null || evidenceImages.isEmpty()) {
            bot.getIO().send(IO.DefinedChannel.DeploymentWarnings, target.getAsMention() + " - " + status + " - " + reason + "\nInitiated by: `" + executor.getUser().getName()+ "`\n" + evidenceText);
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
                        bot.getIO().send(IO.DefinedChannel.DeploymentWarnings, target.getAsMention() + " - " + status + " - " + finalReason + "\nInitiated by: `" + executor.getUser().getName() + "`\n" + finalEvidenceText, uploads);
                    });
        }
    }

    private MessageEmbed generatePunishEmbed(List<String> violations, Member target, boolean ban, String notes, String iconUrl) {
        EmbedBuilder embedBuilder = this.getEmbedBuilder(target, ban);
        embedBuilder.setThumbnail(iconUrl);
        SortedMap<Integer, String> ruleViolationsMap = new TreeMap<>();
        for (String violation : violations) {
            JSONObject violationRule = this.modalToRuleBook.get(violation);
            if (violationRule != null) {
                String point = violationRule.getString("desc");
                if (ruleViolationsMap.containsKey(violationRule.getInt("id"))) {
                    ruleViolationsMap.put(violationRule.getInt("id"), ruleViolationsMap.get(violationRule.getInt("id")) + "\n- **" + point + "**");
                } else {
                    ruleViolationsMap.put(violationRule.getInt("id"), this.rulebook.get(violationRule.getInt("id")).getString("desc") + " Specifically:\n- **" + point + "**");
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

    private EmbedBuilder getEmbedBuilder(Member target, boolean ban) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (ban) {
            embedBuilder.setColor(Color.RED);
            embedBuilder.setTitle("Permanently Banned from " + target.getGuild().getName());
            embedBuilder.setFooter("This ban is indefinite.");
        } else {
            embedBuilder.setColor(Color.YELLOW);
            embedBuilder.setTitle("Removed from " + target.getGuild().getName());
            embedBuilder.setFooter("Follow the rules next time to avoid future removals.");
        }
        embedBuilder.setDescription("You have been " + (ban ? "permanently banned" : "removed") + " from **" + target.getGuild().getName() + "** for violating the following rule(s) below:");
        return embedBuilder;
    }

    private void buildRulebook(JSONObject json) {
        if (json == null) return;
        JSONArray apple = json.getJSONArray("content");
        for (int i = 0; i < apple.length(); i++) {
            JSONObject rule = apple.getJSONObject(i);
            if (!rule.has("id") || !rule.has("title") || !rule.has("desc")) {
                bot.getIO().send(IO.DefinedChannel.DebugAutoModAlert, "Rulebook entry " + (i) + " is missing fields 'id', 'title', 'desc'. Disabling Rulebook and ezpunish");
                this.rulebook = null;break;
            }
            if (this.rulebook.containsKey(rule.getInt("id"))) {
                bot.getIO().send(IO.DefinedChannel.DebugAutoModAlert, "Rulebook entry " + (i) + " has a duplicate ID of " + rule.getInt("id") + ". Ignoring duplicate.");
            } else {
                this.rulebook.put(rule.getInt("id"), rule);
            }
        }
        for (int i = 0; i < json.getJSONArray("keywords").length(); i++) {
            JSONObject rule = json.getJSONArray("keywords").getJSONObject(i);
            modalToRuleBook.put("ezpunish:" + rule.getString("modal"), rule);
        }
        if (json.getJSONArray("keywords") != null) {
            StringSelectMenu.Builder builder = StringSelectMenu.create("ezpunish:violations");
            for (int i = 0; i < json.getJSONArray("keywords").length(); i++) {
                JSONObject rule = json.getJSONArray("keywords").getJSONObject(i);
                builder.addOption(rule.getString("title"), "ezpunish:"+rule.getString("modal"), rule.getString("desc"));
            }
            builder.setMinValues(1);
            builder.setMaxValues(4);
            this.violationsDropdown = builder.build();
        }
    }

}