package org.backblue.scanners;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
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
import org.backblue.commands.EZPunish;
import org.backblue.core.Bot;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProfileScanExtension extends ListenerAdapter {

    private final Map<Integer, EZPunishInfo> map = new HashMap<>();
    Bot bot;
    EZPunish ezpunish;

    public ProfileScanExtension(Bot bot, EZPunish ezpunish) {
        this.bot = bot;
        this.ezpunish = ezpunish;
    }

    public Container create(Member member, String type, int points) {
        int id = this.generate();
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
        map.put(id, new EZPunishInfo(member.getId(), type));
        return container;
    }

    private int generate() {
        int i = 1;
        while (i < 1 || map.containsKey(i)) {
            i = (int) (Math.random() * Short.MAX_VALUE);
        }
        return i;
    }

    @Override
    public void onButtonInteraction(@NonNull ButtonInteractionEvent event) {
        if (event.getButton().getCustomId() == null) {
            return;
        }
        int id = Integer.parseInt(event.getButton().getCustomId().split(";")[0]);
        if (map.containsKey(id)) {
            EZPunishInfo info = map.get(id);
            String action = event.getButton().getCustomId().split(";")[1];
            Member member = bot.getDeploymentGuild().getMemberById(info.memberID());
            if (member == null) return;
            if (action.equals("kick")) ezpunish.ezPunish(member, event.getMember(), List.of("ezpunish:compromisedaccount"), false, member.getEffectiveAvatarUrl(), null);
            if (action.equals("ban")) ezpunish.ezPunish(member, event.getMember(), List.of("ezpunish:compromisedaccount"), true, member.getEffectiveAvatarUrl(), null);
            MessageComponentTree disableAll = event.getMessage().getComponentTree().replace(ComponentReplacer.byUniqueId(100, TextDisplay.of("-# Action taken <t:" + Instant.now().toEpochMilli()/1000 + ":R> by `" + Objects.requireNonNull(event.getMember()).getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")));
            event.editComponents(disableAll.asDisabled()).useComponentsV2(true).queue();
        }
    }

    private record EZPunishInfo(String memberID, String type) {}
}
