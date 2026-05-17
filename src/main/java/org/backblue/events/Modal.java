package org.backblue.events;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.backblue.commands.Badge;
import org.backblue.commands.EZPunish;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class Modal extends ListenerAdapter {

    private final Badge badge;
    private final EZPunish ezpunish;

    public Modal(Badge badge, EZPunish ezpunish) {
        this.badge = badge;
        this.ezpunish = ezpunish;
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("modal:badge") && event.getMember() != null) {
            @NotNull String badge = Objects.requireNonNull(event.getValue("badge:selection")).getAsStringList().getFirst();
            event.deferReply().setEphemeral(true).queue();
            event.getHook().sendMessage(this.badge.changeBadge(event.getMember(), badge)).setEphemeral(true).queue();
        }
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
            event.reply(ezpunish.ezPunish(user, event.getMember(), violations, softban, null, attachments, notes)).setEphemeral(true).queue();
        }
    }
}