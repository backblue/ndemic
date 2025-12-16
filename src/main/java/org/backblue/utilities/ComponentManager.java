package org.backblue.utilities;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.commands.EZPunish;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ComponentManager extends ListenerAdapter {

    private static final Map<Integer, ComponentInteractionEvent> componentsList = new HashMap<>();

    public enum ComponentPreset {
        MESSAGE,
        PROFILE_PICTURE,
        BANNER,
        CUSTOM_STATUS,
        OTHER
    }
    public static ComponentInteractionEvent message(Member member, String messageViolation) {
        if (member == null) {
            return null;
        }

        int id = generateID();

        Container container = Container.of(
                TextDisplay.of("# :warning: Moderator Action Recommended"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of("A user has potentially sent an inappropriate message and has been blocked from sending messages in this guild."),
                        TextDisplay.of("## Offending Message sent from: " + member.getUser().getAsMention() + "\n> " + messageViolation)
                ),
                Section.of(
                        Button.danger(id+";message:kick", "Softban"),
                        TextDisplay.of("**Softban, Log, Warn**\nTemporary removes this user from the guild")
                ).withUniqueId(101),
                Section.of(
                        Button.danger(id+";message:ban", "Ban"),
                        TextDisplay.of("**Ban, Log, Warn**\nPermanently removes this user from the guild")
                ).withUniqueId(102),
                Section.of(
                        Button.primary(id+";message:nothing", "Do nothing"),
                        TextDisplay.of("**Do nothing**\nLocks all the button options")
                ).withUniqueId(103),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# The offending content has already been removed.").withUniqueId(100)

        ).withUniqueId(id);
        Map<String, String> content = new HashMap<>();
        content.put("id", member.getId());
        content.put("message", messageViolation);
        return new ComponentInteractionEvent(ComponentPreset.MESSAGE, container, id, content);
    }
    public static ComponentInteractionEvent profilePicture(Member member, String profileLink, String feedback) {
        if (member == null) {
            return null;
        }
        int id = generateID();

        Container container = Container.of(
                TextDisplay.of("# :warning: Moderator Action Recommended"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of("An user may potentially have an inappropriate profile picture."),
                        TextDisplay.of("## Details: " + member.getUser().getAsMention() + "\n> " + feedback)
                ),
                Section.of(
                        Button.danger(id+";profile_picture:kick", "Softban"),
                        TextDisplay.of("**Softban, Log, Warn**\nTemporary removes this user from the guild")
                ).withUniqueId(101),
                Section.of(
                        Button.danger(id+";profile_picture:ban", "Ban"),
                        TextDisplay.of("**Ban, Log, Warn**\nPermanently removes this user from the guild")
                ).withUniqueId(102),
                Section.of(
                        Button.primary(id+";profile_picture:nothing", "Do nothing"),
                        TextDisplay.of("**Do nothing**\nLocks all the button options")
                ).withUniqueId(103),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# No action has been taken so far.").withUniqueId(100)

        ).withUniqueId(id);
        Map<String, String> content = new HashMap<>();
        content.put("id", member.getId());
        content.put("link", profileLink);
        return new ComponentInteractionEvent(ComponentPreset.PROFILE_PICTURE, container, id, content);
    }
    public static ComponentInteractionEvent banner(Member member, String bannerLink, String feedback) {
        if (member == null) {
            return null;
        }
        int id = generateID();

        Container container = Container.of(
                TextDisplay.of("# :warning: Moderator Action Recommended"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of("An user may potentially have an inappropriate profile banner."),
                        TextDisplay.of("## Details: " + member.getUser().getAsMention() + "\n> " + feedback)
                ),
                Section.of(
                        Button.danger(id+";profile_picture:kick", "Softban"),
                        TextDisplay.of("**Softban, Log, Warn**\nTemporary removes this user from the guild")
                ).withUniqueId(101),
                Section.of(
                        Button.danger(id+";profile_picture:ban", "Ban"),
                        TextDisplay.of("**Ban, Log, Warn**\nPermanently removes this user from the guild")
                ).withUniqueId(102),
                Section.of(
                        Button.primary(id+";profile_picture:nothing", "Do nothing"),
                        TextDisplay.of("**Do nothing**\nLocks all the button options")
                ).withUniqueId(103),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# No action has been taken so far.").withUniqueId(100)

        ).withUniqueId(id);
        Map<String, String> content = new HashMap<>();
        content.put("id", member.getId());
        content.put("link", bannerLink);
        return new ComponentInteractionEvent(ComponentPreset.BANNER, container, id, content);
    }
    public static ComponentInteractionEvent customStatus(Member member, String violation) {
        if (member == null) {
            return null;
        }
        int id = generateID();

        Container container = Container.of(
                TextDisplay.of("# :warning: Moderator Action Recommended"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of("An user may potentially have an  inappropriate custom status."),
                        TextDisplay.of("## Details: " + member.getUser().getAsMention() + "\n> " + violation)
                ),
                Section.of(
                        Button.danger(id+";profile_picture:kick", "Softban"),
                        TextDisplay.of("**Softban, Log, Warn**\nTemporary removes this user from the guild")
                ).withUniqueId(101),
                Section.of(
                        Button.danger(id+";profile_picture:ban", "Ban"),
                        TextDisplay.of("**Ban, Log, Warn**\nPermanently removes this user from the guild")
                ).withUniqueId(102),
                Section.of(
                        Button.primary(id+";profile_picture:nothing", "Do nothing"),
                        TextDisplay.of("**Do nothing**\nLocks all the button options")
                ).withUniqueId(103),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# Ensure that the current custom status matches what is shown by the bot.").withUniqueId(100)

        ).withUniqueId(id);
        Map<String, String> content = new HashMap<>();
        content.put("id", member.getId());
        content.put("link", violation);
        return new ComponentInteractionEvent(ComponentPreset.MESSAGE, container, id, content);
    }
    public static ComponentInteractionEvent other(Member member, String... details) {
        if (details.length < 1 || member == null) {
            return null;
        }
        int id = generateID();
        StringBuilder strBuild = new StringBuilder();
        for (int i = 1; details.length > 1 && i < details.length; i++) {
            strBuild.append(details[i]).append("\n");
        }
        if (strBuild.isEmpty()) {
            strBuild.append("*No additional details provided.*");
        }

        Container container = Container.of(
                TextDisplay.of("# :mag: Additional Review Suggested"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of("**Bot thinks it is:** " + details[0]),
                        TextDisplay.of("## Details: " + member.getUser().getAsMention() + "\n" + strBuild)
                ),
                Section.of(
                        Button.danger(id+";profile_picture:kick", "Softban"),
                        TextDisplay.of("**Softban, Log, Warn**\nTemporary removes this user from the guild")
                ).withUniqueId(101),
                Section.of(
                        Button.danger(id+";profile_picture:ban", "Ban"),
                        TextDisplay.of("**Ban, Log, Warn**\nPermanently removes this user from the guild")
                ).withUniqueId(102),
                Section.of(
                        Button.primary(id+";profile_picture:nothing", "Do nothing"),
                        TextDisplay.of("**Do nothing**\nLocks all the button options")
                ).withUniqueId(103),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# The user is in timeout for 28 days.").withUniqueId(100)

        ).withUniqueId(id);
        Map<String, String> content = new HashMap<>();
        content.put("id", member.getId());
        content.put("details.length", String.valueOf(details.length));
        for (int i = 0; i < details.length; i++) {
            content.put("details." + i, details[i]);
        }
        return new ComponentInteractionEvent(ComponentPreset.MESSAGE, container, id, content);
    }

    private static int generateID() {
        int id = (int) (Math.random() * Integer.MAX_VALUE);
        while (componentsList.containsKey(id)) {
            id = (int) (Math.random() * Integer.MAX_VALUE);
        }
        return id;
    }

    public record ComponentInteractionEvent(ComponentPreset preset, Container container, int id, Map<String, String> content) {
            public ComponentInteractionEvent(ComponentPreset preset, Container container, int id, Map<String, String> content) {
                this.preset = preset;
                this.id = id;
                this.container = container;
                this.content = content;
                componentsList.put(id, this);
            }
        }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getCustomId() == null) {
            return;
        }
        int id = Integer.parseInt(event.getButton().getCustomId().split(";")[0]);
        String item = (event.getButton().getCustomId().split(";")[1]);
        ComponentInteractionEvent cie = componentsList.remove(id);
        if (cie == null) {
            return;
        }
        if (event.getGuild() == null && !EZPunish.enabled()) {
            return;
        }
        Member target = event.getGuild().getMemberById(cie.content.get("id"));
        String action = item.split(":")[1];
        switch (item.split(":")[0].toUpperCase()) {
            case "MESSAGE" -> {
                String offendingMessage = cie.content.get("message");
                switch (action) {
                    case "kick" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), false, "**Offending Message**:\n" + offendingMessage, null);
                    case "ban" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), true, "**Offending Message**:\n" + offendingMessage, null);
                    case "nothing" -> {
                        if (target != null) {
                            target.removeTimeout().queue();
                        }
                    }
                }
            }
            case "PROFILE_PICTURE", "BANNER" -> {
                String offendingMessage = cie.content.get("link");
                switch (action) {
                    case "kick" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), false, offendingMessage, null);
                    case "ban" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), true, offendingMessage, null);
                    case "nothing" -> {
                    }
                }
            }
            case "CUSTOM_STATUS" -> {
                String offendingMessage = cie.content.get("link");
                switch (action) {
                    case "kick" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), false, "**Status**: \n> " + offendingMessage, null);
                    case "ban" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), true, "**Status**: \n> " + offendingMessage, null);
                    case "nothing" -> {
                    }
                }
            }
            case "OTHER" -> {
                switch (action) {
                    case "kick" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), false, cie.content.get("details.0"), null);
                    case "ban" -> Bot.getBot().ezPunish(target, event.getMember(), List.of("ezpunish:profile"), true, cie.content.get("details.0"), null);
                    case "nothing" -> {
                        if (target != null) {
                            target.removeTimeout().queue();
                        }
                    }
                }
            }
            default -> {}
        }
        MessageComponentTree disableAll = event.getMessage().getComponentTree().replace(ComponentReplacer.byUniqueId(100, TextDisplay.of("-# Action taken " + TimeFormat.relativeTime(Instant.now().toEpochMilli()/1000) + " by `" + Objects.requireNonNull(event.getMember()).getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")));
        event.editComponents(disableAll.asDisabled()).useComponentsV2(true).queue();
    }
}
