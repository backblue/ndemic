package org.backblue.core.containers;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.commands.EZPunish;
import org.backblue.core.Bot;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Centralized container/components systems.<br>
 *  All container components, buttons functionality are defined in this class.
 */
public final class Interactive extends ListenerAdapter {

    private static final int Footer_Note = 101;
    private static final int Action_Buttons = 100;

    private final Bot bot;
    private final EZPunish ezPunish;
    private final Map<Short, Interactive.Type> actions = new ConcurrentHashMap<>();

    public Interactive(Bot bot, EZPunish ezPunish) {
        this.bot = bot;
        this.ezPunish = ezPunish;
    }

    @Override
    public void onButtonInteraction(@NonNull ButtonInteractionEvent event) {
        if (event.getButton().getCustomId() == null) return;
        try {
            short id = Short.parseShort(event.getButton().getCustomId().split(";")[0]);
            Type interactive = actions.get(id);
            switch (interactive) {
                case Type.Profile data -> {
                    String punishment = event.getButton().getCustomId().split(";")[1];
                    Member member = bot.getDeploymentGuild().getMemberById(data.memberID);
                    if (member != null) {
                        if (punishment.equals("kick")) this.ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:profile"), false, member.getEffectiveAvatarUrl(), null);
                        if (punishment.equals("ban")) ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:profile"), true, member.getEffectiveAvatarUrl(), null);
                    }
                    MessageComponentTree disableAll = event.getMessage().getComponentTree().replace(ComponentReplacer.byUniqueId(Footer_Note, TextDisplay.of("-# Action taken <t:" + Instant.now().toEpochMilli()/1000 + ":R> by `" + Objects.requireNonNull(event.getMember()).getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")));
                    event.editComponents(disableAll.asDisabled()).useComponentsV2(true).queue();
                }
                case Type.Spam data -> {
                    String punishment = event.getButton().getCustomId().split(";")[1];
                    Member member = bot.getDeploymentGuild().getMemberById(data.memberID);
                    if (member != null) {
                        if (punishment.equals("kick")) this.ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:spam"), false, member.getEffectiveAvatarUrl(), null);
                        if (punishment.equals("ban")) ezPunish.ezPunish(member, event.getMember(), List.of("ezpunish:spam"), true, member.getEffectiveAvatarUrl(), null);
                    }
                    MessageComponentTree disableAll = event.getMessage().getComponentTree().replace(ComponentReplacer.byUniqueId(Footer_Note, TextDisplay.of("-# Action taken <t:" + Instant.now().toEpochMilli()/1000 + ":R> by `" + Objects.requireNonNull(event.getMember()).getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")));
                    event.editComponents(disableAll.asDisabled()).useComponentsV2(true).queue();
                }
                case Type.Gatekeeper data -> {
                    String action = event.getButton().getCustomId().split(";")[1];
                    if (action.equals("nothing")) {
                        MessageComponentTree disableAll = event.getMessage().getComponentTree().replace(ComponentReplacer.byUniqueId(Footer_Note, TextDisplay.of("-# Interaction locked <t:" + Instant.now().toEpochMilli()/1000 + ":R> by `" + Objects.requireNonNull(event.getMember()).getEffectiveName() + "`.")));
                        event.editComponents(disableAll.asDisabled()).useComponentsV2(true).queue();
                        return;
                    }
                    List<String> list = data.memberIDs();
                    StringSelectMenu.Builder selectMenu = StringSelectMenu.create("gatekeeper:target").setRequired(true).setRequiredRange(1, 8);
                    for (String memberID : list) {
                        Member member = bot.getDeploymentGuild().getMemberById(memberID);
                        if (member != null) selectMenu.addOption(member.getUser().getName(), memberID, member.getEffectiveName());
                    }
                    Modal modal = Modal.create("modal:gatekeeper", "Remove Spambots")
                            .addComponents(
                                    TextDisplay.of("Spambots are not notified when they're removed."),
                                    Label.of("Eligible Targets", selectMenu.build()),
                                    Label.of("Punishment", RadioGroup.create("gatekeeper:type")
                                            .addOption("Softban", "softban")
                                            .addOption("Ban", "ban")
                                            .build())
                            ).build();
                    event.replyModal(modal).queue();
                }
                default -> {}
            }
        } catch (NumberFormatException ignored) {}
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
                ).withUniqueId(Interactive.Footer_Note),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# Punishment actions will notify the user (and logged).").withUniqueId(Interactive.Footer_Note)

        ).withUniqueId(id);

        actions.put(id, new Type.Profile(member.getId(), type));
        return container;
    }
    public Container createSpam(Member member, List<File> attachments) {
        short id = this.generate();
        Container container = Container.of(
                TextDisplay.of("# :warning: Messages Blocked"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of(member.getUser().getAsMention() + "'s messages have been flagged for spam."),
                        TextDisplay.of("## Details:\n> Sent **" + attachments.size() + "** images containing scams/invites.")
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
                ).withUniqueId(Interactive.Action_Buttons),
                
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# These messages have already been deleted.").withUniqueId(Interactive.Footer_Note)

        ).withUniqueId(id);
        actions.put(id, new Type.Spam(member.getId()));
        return container;
    }
    public Container createGatekeeper(List<String> memberIDs, long timeUntilScan) {
        short id = this.generate();
        String icon = bot.getDeploymentGuild().getIconUrl() == null ? "" : bot.getDeploymentGuild().getIconUrl();

        StringBuilder list = new StringBuilder();
        for (String memberID : memberIDs) {
            list.append("<@").append(memberID).append("> ");
        }

        Container container = Container.of(
                TextDisplay.of("# :shield: Recent Join Activity"),
                Section.of(
                        Thumbnail.fromUrl(icon),
                        TextDisplay.of(String.format("From **<t:%s:t>** to **<t:%s:t>**, **%,d potential spambot(s)** joined.", timeUntilScan, OffsetDateTime.now().toEpochSecond(), memberIDs.size())),
                        TextDisplay.of(String.format("## Details:\n> %s", list))
                ),
                ActionRow.of(
                        Button.primary(id+";action", "Take action..."),
                        Button.secondary(id+";nothing", "Do nothing")
                ).withUniqueId(Interactive.Action_Buttons),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# AI may make mistakes; use final judgement.").withUniqueId(Interactive.Footer_Note)

        ).withUniqueId(id);
        actions.put(id, new Type.Gatekeeper(memberIDs));
        return container;
    }

    sealed interface Type {
        record Profile(String memberID, String type) implements Type {}
        record Spam(String memberID) implements Type {}
        record Gatekeeper(List<String> memberIDs) implements Type {}
    }
}
