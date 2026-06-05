package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class EZPunish extends ListenerAdapter {

    Bot bot;
    private HashMap<Integer, JSONObject> rulebook = new HashMap<>();
    private final HashMap<String, JSONObject> modalToRuleBook = new HashMap<>();
    private StringSelectMenu violationsDropdown = null;
    private final Map<String, String> ezPunishCache;

    public EZPunish(Bot bot, JSONObject json) {
        this.bot = bot;
        buildRulebook(json);
        ezPunishCache = new HashMap<>();
    }

    public String ezPunish(Member target, Member executor, List<String> violations, boolean ban, String evidenceText, List<Message.Attachment> evidenceImages) {
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
        bot.getIO().send(target.getUser(), "", generatePunishEmbed(violations, target, ban, target.getGuild().getIconUrl()));
        logToWarnings(violations, target, ban, evidenceText, evidenceImages, executor);
        target.ban(1, TimeUnit.HOURS).reason("Moderator initiated by " + executor.getUser().getName()).queue();
        bot.getScheduler().schedule(() -> {
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
            bot.getIO().send(DefinedChannel.DeploymentWarnings, target.getAsMention() + " - " + status + " - " + reason + "\nInitiated by: `" + executor.getUser().getName()+ "`\n" + evidenceText);
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
                        bot.getIO().send(DefinedChannel.DeploymentWarnings, target.getAsMention() + " - " + status + " - " + finalReason + "\nInitiated by: `" + executor.getUser().getName() + "`\n" + finalEvidenceText, uploads);
                    });
        }
    }

    private MessageEmbed generatePunishEmbed(List<String> violations, Member target, boolean ban, String iconUrl) {
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
                bot.getIO().send(DefinedChannel.DebugAutoModAlert, "Rulebook entry " + (i) + " is missing fields 'id', 'title', 'desc'. Disabling Rulebook and ezpunish");
                this.rulebook = null;
                break;
            }
            if (this.rulebook.containsKey(rule.getInt("id"))) {
                bot.getIO().send(DefinedChannel.DebugAutoModAlert, "Rulebook entry " + (i) + " has a duplicate ID of " + rule.getInt("id") + ". Ignoring duplicate.");
            } else {
                this.rulebook.put(rule.getInt("id"), rule);
            }
        }
        for (int i = 0; i < json.getJSONArray("keywords").length(); i++) {
            JSONObject rule = json.getJSONArray("keywords").getJSONObject(i);
            modalToRuleBook.put("ezpunish:"+rule.getString("title").replace("-", "").toLowerCase().replace(":", "").replace(" ", "").trim(), rule);
        }
        if (json.getJSONArray("keywords") != null) {
            StringSelectMenu.Builder builder = StringSelectMenu.create("ezpunish:violations");

            List<JSONObject> sortedKeywords = new ArrayList<>();
            for (int i = 0; i < json.getJSONArray("keywords").length(); i++) {
                sortedKeywords.add(json.getJSONArray("keywords").getJSONObject(i));
            }
            sortedKeywords.sort((a, b) -> a.getString("title").compareToIgnoreCase(b.getString("title")));

            for (JSONObject rule : sortedKeywords) {
                builder.addOption(rule.getString("title"), "ezpunish:"+rule.getString("title").replace("-", "").toLowerCase().replace(":", "").replace(" ", "").trim(), rule.getString("desc"));
            }
            builder.setMinValues(1);
            builder.setMaxValues(4);
            this.violationsDropdown = builder.build();
        }
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
            Modal modal = Modal.create("modal:ezpunish", "Moderation Action")
                    .addComponents(
                            Label.of("Target", menu),
                            Label.of("Punishment", punishment),
                            Label.of("Violations", violationsDropdown),
                            Label.of("Attachments / Evidence", AttachmentUpload.create("ezpunish:evidence").setRequired(false).build())
                    )
                    .build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onMessageContextInteraction(@NonNull MessageContextInteractionEvent event) {
        if (event.getName().equals("EZPunish...")) {
            if (event.getTarget().getMember() == null) {
                event.reply(":x: No user in server.").setEphemeral(true).queue();
                return;
            }
            if (this.rulebook == null) {
                event.reply(":x: The rulebook is not configured!").setEphemeral(true).queue();
                return;
            }
            if (event.getTarget().getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(":x: Don't try using this on discord staff.").setEphemeral(true).queue();
                return;
            }
            String text = "*No text is included in this message.*";
            if (!event.getTarget().getContentStripped().isEmpty()) {
                text = ">>> " + event.getTarget().getContentStripped();
            }
            if (event.getTarget().getType().equals(MessageType.AUTO_MODERATION_ACTION)) {
                text = event.getTarget().getJumpUrl();
            }
            EntitySelectMenu menu = EntitySelectMenu.create("ezpunish:target", EntitySelectMenu.SelectTarget.USER)
                    .setDefaultValues(EntitySelectMenu.DefaultValue.user(event.getTarget().getAuthor().getId())).build();
            RadioGroup punishment = RadioGroup.create("ezpunish:type")
                    .addOption("Softban (+1hr removal of messages)", "softban")
                    .addOption("Ban (+1hr removal of messages)", "ban")
                    .build();
            Modal modal = Modal.create("modal:ezpunishQuick", "Moderation Action")
                    .addComponents(
                            Label.of("Target", menu),
                            Label.of("Punishment", punishment),
                            Label.of("Violations", violationsDropdown),
                            TextDisplay.of("### Evidence (+" + event.getTarget().getAttachments().size() + " attachments)\n" + text)
                    ).build();
            event.replyModal(modal).queue();
            if (text.equals("*No text is included in this message.*")) {
                text = "";
            }
            this.ezPunishCache.put(event.getTarget().getAuthor().getId(), text);
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().contains("modal:ezpunish")) {
            @NotNull Member user = Objects.requireNonNull(event.getValue("ezpunish:target")).getAsMentions().getMembers().getFirst();
            @NotNull String type = Objects.requireNonNull(event.getValue("ezpunish:type")).getAsString();
            @NotNull List<String> violations = Objects.requireNonNull(event.getValue("ezpunish:violations")).getAsStringList();
            List<Message.Attachment> attachments = null;
            if (event.getMember() == null) return;
            if (event.getValue("ezpunish:evidence") != null) {
                attachments = Objects.requireNonNull(event.getValue("ezpunish:evidence")).getAsAttachmentList();
            }

            boolean softban = type.equalsIgnoreCase("softban");
            String textEvidence = null;
            if (event.getModalId().equals("modal:ezpunishQuick")) {
                textEvidence = this.ezPunishCache.remove(user.getId());
            }

            event.reply(this.ezPunish(user, event.getMember(), violations, !softban, textEvidence, attachments)).setEphemeral(true).queue();
        }
    }
}