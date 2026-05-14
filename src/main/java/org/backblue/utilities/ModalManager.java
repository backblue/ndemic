package org.backblue.utilities;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.backblue.Bot;
import org.backblue.commands.Badge;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class ModalManager extends ListenerAdapter {

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("modal:ezpunish")) {
            @NotNull Member user = Objects.requireNonNull(event.getValue("ezpunish:target")).getAsMentions().getMembers().getFirst();
            @NotNull String type = Objects.requireNonNull(event.getValue("ezpunish:type")).getAsString();
            @NotNull List<String> violations = Objects.requireNonNull(event.getValue("ezpunish:violations")).getAsStringList();
            List<Message.Attachment> attachments = null;
            if (event.getMember() == null) return;
            if (event.getValue("ezpunish:evidence") != null) {
                attachments = Objects.requireNonNull(event.getValue("ezpunish:evidence")).getAsAttachmentList();
            }

            String notes = null;
            try {
                ModalMapping additionalNotes = event.getValue("ezpunish:note");
                if (additionalNotes != null && !additionalNotes.getAsString().isEmpty()) {
                    notes = additionalNotes.getAsString();
                }
            } catch (Exception ignored) {}
            boolean softban = !type.equals("Softban");
            if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(Bot.getBot().ezPunish(user, event.getMember(), violations, softban, null, attachments, notes).message()).queue();
                return;
            }
            event.reply(Bot.getBot().ezPunish(user, event.getMember(), violations, softban, null, attachments, notes).message()).setEphemeral(true).queue();
        }
        if (event.getModalId().equals("modal:badge") && event.getMember() != null) {
            @NotNull String badge = Objects.requireNonNull(event.getValue("badge:selection")).getAsStringList().getFirst();
            event.deferReply().setEphemeral(true).queue();
            event.getHook().sendMessage(Bot.getBot().getBadgeSystem().changeBadge(event.getMember(), badge)).setEphemeral(true).queue();
        }
        if (event.getModalId().equals("modal:quickezpunish")) {
            @NotNull Member user = Objects.requireNonNull(event.getValue("quickezpunish:target")).getAsMentions().getMembers().getFirst();
            @NotNull String type = Objects.requireNonNull(event.getValue("quickezpunish:type")).getAsStringList().getFirst();
            @NotNull List<String> violations = Objects.requireNonNull(event.getValue("ezpunish:violations")).getAsStringList();
            if (event.getMember() == null) return;
            ContextManager.EZPunishContext context = ContextManager.RetrieveContext(user.getId());
            if (context == null) {
                event.reply(":x: This interaction expired").setEphemeral(true).queue();
                return;
            }
            String notes = null;
            try {
                ModalMapping additionalNotes = event.getValue("quickezpunish:note");
                if (additionalNotes != null && !additionalNotes.getAsString().isEmpty()) {
                    notes = additionalNotes.getAsString();
                }
            } catch (Exception ignored) {}
            boolean softban = type.equals("Softban");
            event.reply(Bot.getBot().ezPunish(user, event.getMember(), violations, softban, context.text(), context.attachments(), notes).message()).setEphemeral(true).queue();
        }
    }
}
