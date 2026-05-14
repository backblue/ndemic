package org.backblue.utilities;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.backblue.commands.EZPunish;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ContextManager extends ListenerAdapter {

    private static final Map<String, EZPunishContext> CONTEXT_CACHE = new ConcurrentHashMap<>();

    public static EZPunishContext RetrieveContext(String id) {
        return CONTEXT_CACHE.remove(id);
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent event) {
        if (event.getName().contains("EZPunish")) {
            if (event.getTarget().getMember() == null) {
                event.reply(":x: No user in server.").setEphemeral(true).queue();
                return;
            }
            if (EZPunish.getViolationsDropdown() == null) {
                event.reply(":x: The rulebook is not configured!").setEphemeral(true).queue();
                return;
            }
            if (event.getTarget().getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(":x: Don't try using this on discord staff.").setEphemeral(true).queue();
                return;
            }
            EntitySelectMenu menu = EntitySelectMenu.create("quickezpunish:target", EntitySelectMenu.SelectTarget.USER)
                    .setDefaultValues(EntitySelectMenu.DefaultValue.user(event.getTarget().getAuthor().getId())).build();

            StringSelectMenu punishment;
            StringSelectMenu violations = EZPunish.getViolationsDropdown();

            if (event.getName().contains("EZPunish:")) {
                punishment = StringSelectMenu.create("quickezpunish:type")
                        .addOption("Softban", "softban", "Kicks the user and deletes an hour of messages")
                        .addOption("Ban", "ban", "Bans the user and deletes an hour of messages")
                        .setDefaultValues("softban")
                        .build();

                if (event.getName().equals("EZPunish: Scam")) {
                    violations = violations.createCopy()
                            .setDefaultValues("ezpunish:compromise", "ezpunish:spamlinks")
                            .build();
                }
                if (event.getName().equals("EZPunish: Spam")) {
                    violations = violations.createCopy()
                            .setDefaultValues("ezpunish:spam")
                            .build();
                }
            } else {
                punishment = StringSelectMenu.create("quickezpunish:type")
                        .addOption("Softban", "softban", "Kicks the user and deletes an hour of messages")
                        .addOption("Ban", "ban", "Bans the user and deletes an hour of messages")
                        .setDefaultValues("softban")
                        .build();
            }
            if (event.getName().equals("EZPunish...")) {
                violations = violations.createCopy()
                        .setDefaultValues("")
                        .build();
            }

            TextInput note = TextInput.create("quickezpunish:note", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Personalized note to the user regarding their punishment")
                    .setMinLength(0)
                    .setMaxLength(1000)
                    .setRequired(false)
                    .build();

            String textmsg = "*No text is included in this message.*";
            if (!event.getTarget().getContentStripped().isEmpty()) {
                textmsg = ">>> " + event.getTarget().getContentStripped();
            }
            if (event.getTarget().getType().equals(MessageType.AUTO_MODERATION_ACTION)) {
                textmsg = event.getTarget().getJumpUrl();
            }
            Modal modal = Modal.create("modal:quickezpunish", "EZPunish... ")
                    .addComponents(
                            Label.of("Target", menu),
                            Label.of("Punishment", punishment),
                            Label.of("Violations", violations),
                            TextDisplay.of("### Evidence (+" + event.getTarget().getAttachments().size() + " attachments)\n" + textmsg),
                            Label.of("Notes", note)
                    )
                    .build();

            event.replyModal(modal).queue();
            if (textmsg.equals("*No text is included in this message.*")) {
                textmsg = "";
            }
            EZPunishContext context = new EZPunishContext(textmsg, event.getTarget().getAttachments());
            ContextManager.CONTEXT_CACHE.put(event.getTarget().getAuthor().getId(), context);
        }
    }

    public record EZPunishContext(String text, List<Message.Attachment> attachments) { }
}
