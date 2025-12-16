package org.backblue.events;

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
            @NotNull String type = Objects.requireNonNull(event.getValue("ezpunish:type")).getAsStringList().getFirst();
            @NotNull List<String> violations = Objects.requireNonNull(event.getValue("ezpunish:violations")).getAsStringList();
            @NotNull Message.Attachment attachment = Objects.requireNonNull(event.getValue("ezpunish:evidence")).getAsAttachmentList().getFirst();
            String notes = null;
            try {
                ModalMapping additionalNotes = event.getValue("ezpunish:note");
                if (additionalNotes != null && !additionalNotes.getAsString().isEmpty()) {
                    notes = additionalNotes.getAsString();
                }
            } catch (Exception ignored) {}
            boolean softban = !type.equals("Softban");
            event.reply(Bot.getBot().ezPunish(user, event.getMember(), violations, softban, null, attachment, notes).message()).queue();
        }
        if (event.getModalId().equals("modal:badge") && event.getMember() != null) {
            @NotNull String badge = Objects.requireNonNull(event.getValue("badge:selection")).getAsStringList().getFirst();
            event.deferReply().setEphemeral(true).queue();
            event.getHook().sendMessage(Badge.changeBadge(event.getMember(), badge)).setEphemeral(true).queue();
        }
    }
}
