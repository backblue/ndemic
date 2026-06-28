package org.backblue.core;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.commands.EZPunish;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Interactive extends ListenerAdapter {

    private final Bot bot;
    private final EZPunish ezPunish;
    private final Map<Short, InteractiveCore> actions = new ConcurrentHashMap<>();

    public Interactive(Bot bot, EZPunish ezPunish) {
        this.bot = bot;
        this.ezPunish = ezPunish;
    }

    @Override
    public void onButtonInteraction(@NonNull ButtonInteractionEvent event) {
        if (event.getButton().getCustomId() == null) return;
        short id = Short.parseShort(event.getButton().getCustomId().split(";")[0]);
        InteractiveCore interactive = actions.get(id);
        switch (interactive) {
            case InteractiveCore.Profile action -> {
                String punishment = event.getButton().getCustomId().split(";")[1];
                Member member = bot.getDeploymentGuild().getMemberById(action.memberID);
                if (member != null) {
                    if (punishment.equals("kick")) this.ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:profile"), false, member.getEffectiveAvatarUrl(), null);
                    if (punishment.equals("ban")) ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:profile"), true, member.getEffectiveAvatarUrl(), null);
                }
                MessageComponentTree disableAll = event.getMessage().getComponentTree().replace(ComponentReplacer.byUniqueId(100, TextDisplay.of("-# Action taken <t:" + Instant.now().toEpochMilli()/1000 + ":R> by `" + Objects.requireNonNull(event.getMember()).getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")));
                event.editComponents(disableAll.asDisabled()).useComponentsV2(true).queue();
            }
            case InteractiveCore.Spam action -> {
                String punishment = event.getButton().getCustomId().split(";")[1];
                Member member = bot.getDeploymentGuild().getMemberById(action.memberID);
                if (member != null) {
                    if (punishment.equals("kick")) this.ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:spam"), false, member.getEffectiveAvatarUrl(), null);
                    if (punishment.equals("ban")) ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:spam"), true, member.getEffectiveAvatarUrl(), null);
                }
                MessageComponentTree disableAll = event.getMessage().getComponentTree().replace(ComponentReplacer.byUniqueId(100, TextDisplay.of("-# Action taken <t:" + Instant.now().toEpochMilli()/1000 + ":R> by `" + Objects.requireNonNull(event.getMember()).getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")));
                event.editComponents(disableAll.asDisabled()).useComponentsV2(true).queue();
            }
        }

    }

    private short generate() {
        short i = 1;
        while (i < 1 || actions.containsKey(i)) i = (short) (Math.random() * Short.MAX_VALUE);
        return i;
    }

    public Container createProfile(Member member, String type, int points) {
        short id = this.generate();
        Container container = Container.of(
                TextDisplay.of("# :triangular_flag_on_post: Moderator Review Recommended"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of(member.getUser().getAsMention() + "'s profile " + type + " is flagged for further review."),
                        TextDisplay.of("## Details: " + member.getUser().getAsMention() +"\n> Severity value is " + points )
                ),
                ActionRow.of(
                        Button.danger(id+";kick", "Softban"),
                        Button.danger(id+";ban", "Ban"),
                        Button.secondary(id+";nothing", "Do nothing")
                ).withUniqueId(101),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# Punishment actions will notify the user (and logged).").withUniqueId(100)

        ).withUniqueId(id);
        actions.put(id, new InteractiveCore.Profile(member.getId(), type));
        return container;
    }
    public Container createSpam(Member member, List<File> attachments) {
        short id = this.generate();
        Container container = Container.of(
                TextDisplay.of("# :warning: Messages Blocked").withUniqueId((int) (Math.random() * Short.MAX_VALUE)),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of(member.getUser().getAsMention() + "'s messages have been flagged for spam."),
                        TextDisplay.of("## Details:\n> Sent **" + attachments.size() + "** messages containing crypto scam images.")
                ).withUniqueId((int) (Math.random() * Short.MAX_VALUE)),

                MediaGallery.of(
                        attachments.stream()
                                .map(file -> MediaGalleryItem.fromFile(FileUpload.fromData(file)))
                                .toList()

                ).withUniqueId((int) (Math.random() * Short.MAX_VALUE)),
                ActionRow.of(
                        Button.danger(id+";kick", "Softban"),
                        Button.danger(id+";ban", "Ban"),
                        Button.secondary(id+";nothing", "Do nothing")
                ).withUniqueId(101),

                Separator.createDivider(Separator.Spacing.SMALL).withUniqueId(5),
                TextDisplay.of("-# These messages have already been deleted.").withUniqueId(100)

        ).withUniqueId(id);
        actions.put(id, new InteractiveCore.Spam(member.getId()));
        return container;
    }

    sealed interface InteractiveCore {
        record Profile(String memberID, String type) implements InteractiveCore {}
        record Spam(String memberID) implements InteractiveCore {}
    }

}
