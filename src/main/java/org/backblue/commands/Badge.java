package org.backblue.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Badge extends ListenerAdapter {

    private final Bot bot;
    private final boolean defaultUnlock;
    private final Set<IconProperties> loadedBadges = new HashSet<>();
    private final HashMap<String, IconProperties> codeToBadges = new HashMap<>();
    private final String randomOut = UUID.randomUUID().toString().substring(0, 6);
    private static final Logger Log = LoggerFactory.getLogger(Badge.class);

    public Badge(Bot bot, JSONObject json) {
        this.bot = bot;
        defaultUnlock = json.optBoolean("default", false);
        JSONArray array = json.optJSONArray("content");
        if (array == null) {
            Log.error("Missing badge content");
            bot.disableFeature(FeatureFlag.RoleIcons);
        } else {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item;
                String modalTitle = null;
                String modalID = null;
                String emojiCode = null;
                String emojiRole = null;
                Set<String> requiredRoles = new HashSet<>();
                Set<String> flags = new HashSet<>();
                try {
                    item = array.getJSONObject(i);
                    modalTitle = item.getString("modalTitle");
                    modalID =  item.getString("modalID");
                    emojiCode = item.getString("emojiCode");
                    emojiRole = item.getString("emojiRoleID");
                    for (int j = 0; j < item.getJSONArray("eligibleRoles").length(); j++) {
                        requiredRoles.add(item.getJSONArray("eligibleRoles").getString(j));
                    }
                    for (int j = 0; j < item.getJSONArray("flags").length(); j++) {
                        flags.add(item.getJSONArray("flags").getString(j));
                    }
                } catch (JSONException e) {
                    Log.error("Skipping importing entry {}, error reading data: {}", i, e.getMessage());
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
            if (!event.getGuild().getId().equals(bot.getDeploymentGuild().getId())) {
                event.reply(":x: Deployment guild only").setEphemeral(true).queue();
                return;
            }
            if (!bot.isFeatureEnabled(FeatureFlag.RoleIcons)) {
                event.reply(":x: Temporarily disabled").setEphemeral(true).queue();
                return;
            }
            Set<IconProperties> eligibleIcons = this.eligibleIcons(event.getMember());
            if (eligibleIcons.isEmpty() && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("You have no eligible badges for selection.").setEphemeral(true).queue();
                bot.getIO().send(DefinedChannel.DebugEnforcement, "did not allow user `" + event.getMember().getEffectiveName() + "` to access badge system due to having no unlocked badges.");
                return;
            }
            Modal modal = Modal.create("modal:badge", "Role Icon Selection")
                    .addComponents(
                            TextDisplay.of("Below are all role icons available to you.\n-# \\* This feature is unavailable if the boost level is less than 2."),
                            Label.of("Badge", this.badgeSelectBuild(eligibleIcons))
                    ).build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onGuildMemberUpdateBoostTime(@NotNull GuildMemberUpdateBoostTimeEvent event) {
        if (event.getOldTimeBoosted() != null && event.getNewTimeBoosted() == null) {
            for (IconProperties iconProperties : loadedBadges) {
                Role role = bot.getJDA().getRoleById(iconProperties.emojiRole);
                if (role == null) continue;
                if (event.getMember().getUnsortedRoles().contains(role)) {
                    if (!eligibleIcons(event.getMember()).contains(iconProperties)) {
                        this.changeBadge(event.getMember(), "badge:" + randomOut);
                    }
                }
            }
        } else if (event.getOldTimeBoosted() == null && event.getNewTimeBoosted() != null && bot.isFeatureEnabled(FeatureFlag.NitroBoostMessage)) {
            bot.getIO().send(event.getUser(), "Thanks for boosting **" + event.getGuild().getName() + "**\nFor your thanks, server boosters can select a badge to display next to their name!\nAllow " + event.getJDA().getSelfUser().getAsMention() + " to handle the `/badge` command in <#796358850735243264>");
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("modal:badge") && event.getMember() != null) {
            @NotNull String badge = Objects.requireNonNull(event.getValue("badge:selection")).getAsStringList().getFirst();
            event.deferReply().setEphemeral(true).queue();
            event.getHook().sendMessage(this.changeBadge(event.getMember(), badge)).setEphemeral(true).queue();
        }
    }

    public String changeBadge(Member member, String newBadge) {
        for (IconProperties icon : this.loadedBadges) {
            if (member.getUnsortedRoles().contains(bot.getDeploymentGuild().getRoleById(icon.emojiRole))) {
                Role role = bot.getDeploymentGuild().getRoleById(icon.emojiRole);
                if (role != null) bot.getDeploymentGuild().removeRoleFromMember(member, role).queue();
            }
        }
        if (this.codeToBadges.get(newBadge) == null) {
            bot.getIO().send(DefinedChannel.DebugAutoModAlert, "removed badge from user `" + member.getEffectiveName() + "` (`" + member.getId() + "`)");
            return "Badge removed";
        }
        Role newRole = bot.getDeploymentGuild().getRoleById(codeToBadges.get(newBadge).emojiRole);
        if (newRole != null) {
            bot.getIO().send(DefinedChannel.DebugAutoModAlert, "added badge to user `" + member.getEffectiveName() + "` `(" + member.getId() + ")`, `" + newBadge + "`");
            bot.getDeploymentGuild().addRoleToMember(member, newRole).queue();
        }
        return "Badge changed";
    }

    private record IconProperties(String modalTitle, String modalID, String emojiCode, String emojiRole, Set<String> rolesRequirement, Set<String> flags) {}

    private Set<IconProperties> eligibleIcons(Member member) {
        Set<IconProperties> icons = new HashSet<>();
        Role boosterRole = bot.getDeploymentGuild().getBoostRole();
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
                Role role = bot.getDeploymentGuild().getRoleById(roleID);
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