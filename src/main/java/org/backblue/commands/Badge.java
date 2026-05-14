package org.backblue.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.backblue.Bot;
import org.backblue.utilities.NdemicModule;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;


import java.util.*;

public class Badge extends ListenerAdapter implements NdemicModule {

    private final boolean defaultUnlock;
    private final Set<IconProperties> loadedBadges = new HashSet<>();
    private final HashMap<String, IconProperties> codeToBadges = new HashMap<>();
    private final String randomOut = UUID.randomUUID().toString().substring(0, 6);

    public Badge(JSONObject json) {
        defaultUnlock = json.optBoolean("unlock_if_empty", false);
        JSONArray array = json.optJSONArray("content");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String modalTitle = item.getString("modal_title");
                String modalID =  item.getString("modal_id");
                String emojiCode = item.getString("emoji_code");
                String emojiRole = item.getString("emoji_role");
                Set<String> requiredRoles = new HashSet<>();
                for (int j = 0; j < item.getJSONArray("unlock_roles").length(); j++) {
                    requiredRoles.add(item.getJSONArray("unlock_roles").getString(j));
                }
                Set<String> flags = new HashSet<>();
                for (int j = 0; j < item.getJSONArray("flags").length(); j++) {
                    flags.add(item.getJSONArray("flags").getString(j));
                }
                IconProperties iconProperties = new IconProperties(modalTitle, modalID, emojiCode, emojiRole, requiredRoles, flags);
                this.loadedBadges.add(iconProperties);
                this.codeToBadges.put("badge:" + modalID, iconProperties);
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("badge") && event.getMember() != null && event.getGuild() != null) {
            if (event.getGuild().getBoostTier().getKey() < 2) {
                event.reply(":x: This feature is unavailable if the boost level is less than 2.").setEphemeral(true).queue();
                return;
            }
            if (this.loadedBadges.isEmpty()) {
                event.reply(":x: There are no badges available").setEphemeral(true).queue();
                return;
            }
            if (!event.getGuild().getId().equals(Bot.getBot().getDeploymentGuild().getId())) {
                event.reply(":x: Deployment guild only").setEphemeral(true).queue();
                return;
            }
            if (!isEnabled()) {
                event.reply(":x: Temporarily disabled").setEphemeral(true).queue();
                return;
            }
            Set<IconProperties> eligibleIcons = this.eligibleIcons(event.getMember());
            if (eligibleIcons.isEmpty() && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("You have no eligible badges for selection.").setEphemeral(true).queue();
                Bot.getBot().sendDebugMessage("autoMod", "did not allow user `" + event.getMember().getEffectiveName() + "` to access badge system due to having no unlocked badges.");
                return;
            }
            Modal modal = Modal.create("modal:badge", "Badge Selection (Beta)")
                    .addComponents(
                            TextDisplay.of("As a **Server Booster**, you can have any role icon appearing next to your username in messages.\n-# \\* This feature is unavailable if the boost level is less than 2."),
                            Label.of("Badge", this.badgeSelectBuild(eligibleIcons))
                    ).build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onGuildMemberUpdateBoostTime(@NotNull GuildMemberUpdateBoostTimeEvent event) {
        if (event.getOldTimeBoosted() != null && event.getNewTimeBoosted() == null) {
            for (IconProperties iconProperties : loadedBadges) {
                Role role = Bot.getBot().getJDA().getRoleById(iconProperties.emojiRole);
                if (role == null) continue;
                if (event.getMember().getUnsortedRoles().contains(role)) {
                    if (!eligibleIcons(event.getMember()).contains(iconProperties)) {
                        changeBadge(event.getMember(), "badge:" + randomOut);
                    }
                }
            }
        } else if (event.getOldTimeBoosted() == null && event.getNewTimeBoosted() != null) {
            Bot.getBot().sendUserMessage(event.getUser(), "Thanks for boosting **" + event.getGuild().getName() + "**\nFor your thanks, server boosters can select a badge to display next to their name!\nAllow " + event.getJDA().getSelfUser().getAsMention() + " to handle the `/badge` command to select one.");
        }
    }

    public String changeBadge(Member member, String newBadge) {
        for (IconProperties icon : this.loadedBadges) {
            if (member.getUnsortedRoles().contains(Bot.getBot().getDeploymentGuild().getRoleById(icon.emojiRole))) {
                Role role = Bot.getBot().getDeploymentGuild().getRoleById(icon.emojiRole);
                if (role != null) Bot.getBot().getDeploymentGuild().removeRoleFromMember(member, role).queue();
            }
        }
        if (this.codeToBadges.get(newBadge) == null) {
            Bot.getBot().sendDebugMessage("autoMod", "removed badge from user `" + member.getEffectiveName() + "` (" + member.getId() + ")`");
            return "Badge removed";
        }
        Role newRole = Bot.getBot().getDeploymentGuild().getRoleById(codeToBadges.get(newBadge).emojiRole);
        if (newRole != null) {
            Bot.getBot().sendDebugMessage("autoMod", "added badge to user `" + member.getEffectiveName() + "` (" + member.getId() + "), " + newBadge + "`");
            Bot.getBot().getDeploymentGuild().addRoleToMember(member, newRole).queue();
        }
        return "Badge changed";
    }

    @Override
    public String name() {
        return "roleIcons";
    }

    private record IconProperties(String modalTitle, String modalID, String emojiCode, String emojiRole, Set<String> rolesRequirement, Set<String> flags) {}

    private Set<IconProperties> eligibleIcons(Member member) {
        Set<IconProperties> icons = new HashSet<>();
        Role boosterRole = Bot.getBot().getDeploymentGuild().getBoostRole();
        for (IconProperties icon : this.loadedBadges) {
            if (icon.rolesRequirement.isEmpty() && this.defaultUnlock) {
                icons.add(icon);
                continue;
            } else if (icon.flags.contains("booster") && member.getUnsortedRoles().contains(boosterRole)) {
                icons.add(icon);
                continue;
            } else if (icon.flags.contains("unlock") || member.getPermissions().contains(Permission.ADMINISTRATOR)) {
                icons.add(icon);
                continue;
            }
            for (String roleID : icon.rolesRequirement) {
                Role role = Bot.getBot().getDeploymentGuild().getRoleById(roleID);
                if (role == null) continue;
                if (member.getUnsortedRoles().contains(role)) {
                    icons.add(icon);
                }
            }
        }
        return icons;
    }

    private StringSelectMenu badgeSelectBuild(Set<IconProperties> badges) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("badge:selection");
        IconProperties[] list = badges.toArray(new IconProperties[0]);
        for (IconProperties badge : list) {
            menuBuilder.addOption(badge.modalTitle, "badge:" + badge.modalID, Emoji.fromFormatted(badge.emojiCode));
        }
        menuBuilder.addOption("None", "badge:" + this.randomOut);
        menuBuilder.setDefaultValues("badge:none" + this.randomOut);
        menuBuilder.setPlaceholder("Badge...");
        return menuBuilder.build();
    }
}
