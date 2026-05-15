package org.backblue.events;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.commands.Badge;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Modal extends ListenerAdapter {

    private final Badge badge;

    public Modal(Badge badge) {
        this.badge = badge;
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("modal:badge") && event.getMember() != null) {
            @NotNull String badge = Objects.requireNonNull(event.getValue("badge:selection")).getAsStringList().getFirst();
            event.deferReply().setEphemeral(true).queue();
            event.getHook().sendMessage(this.badge.changeBadge(event.getMember(), badge)).setEphemeral(true).queue();
        }
    }
}