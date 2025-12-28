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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Badge extends ListenerAdapter implements NdemicModule {

    private static Boolean ROLE_NEEDED = null;
    private static String ROLE_ID = null;
    private static StringSelectMenu BADGES = null;
    private static final Map<String, String> CACHE = new HashMap<>();
    private static final Set<String> LOADED_ROLES = new HashSet<>();

    static {
        Path path = Path.of("data/badges.json");
        JSONObject fileContent;
        if (Files.exists(path)) {
            try {
                fileContent = new JSONObject(Files.readString(path));
                if (fileContent.has("boosters")) {
                    Badge.ROLE_NEEDED = fileContent.getBoolean("boosters");
                }
                if (fileContent.has("boosterRole")) {
                    Badge.ROLE_ID = fileContent.getString("boosterRole");
                }
                JSONArray array = fileContent.getJSONArray("content");
                StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("badge:selection");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject badge = array.getJSONObject(i);
                    menuBuilder.addOption(badge.getString("title"), "badge:"+badge.getString("id"), Emoji.fromFormatted(badge.getString("code")));
                    CACHE.put("badge:"+badge.getString("id"), badge.getString("role"));
                    LOADED_ROLES.add(badge.getString("role"));
                }
                menuBuilder.addOption("None", "badge:none");
                menuBuilder.setDefaultValues("badge:none");
                menuBuilder.setPlaceholder("Badge...");
                Badge.BADGES = menuBuilder.build();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("badge") && event.getMember() != null && event.getGuild() != null) {
            if (ROLE_NEEDED == null || ROLE_ID == null || BADGES == null) {
                event.reply(":x: Badge system is not configured.").setEphemeral(true).queue();
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
            Role role = event.getGuild().getRoleById(ROLE_ID);
            if (!event.getMember().getRoles().contains(role) && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("You need to have the <@&" + ROLE_ID + "> role to use the badge system.").setEphemeral(true).queue();
                Bot.getBot().sendDebugMessage("autoMod", "did not allow user `" + event.getMember().getEffectiveName() + "` to access badge system due to missing role.");
                return;
            }
            Modal modal = Modal.create("modal:badge", "Badge Selection (Beta)")
                    .addComponents(
                            TextDisplay.of("As a <@&" + ROLE_ID + ">, you can have a role icon appearing next to your username in messages.\n-# \\* This feature is unavailable if the boost level is less than 2."),
                            Label.of("Badge", Badge.BADGES)
                    )
                    .build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onGuildMemberUpdateBoostTime(@NotNull GuildMemberUpdateBoostTimeEvent event) {
        if (event.getOldTimeBoosted() != null && event.getNewTimeBoosted() == null) {
            changeBadge(event.getMember(), "badge:none");
        } else if (event.getOldTimeBoosted() == null && event.getNewTimeBoosted() != null) {
            Bot.getBot().sendUserMessage(event.getUser(), "Thanks for boosting " + event.getGuild().getName() + "! You can now set a badge icon using the `/badge` command.");
        }
    }

    public static String changeBadge(Member member, String newBadge) {
        for (Role role : member.getRoles()) {
            if (LOADED_ROLES.contains(role.getId())) {
                Bot.getBot().getDeploymentGuild().removeRoleFromMember(member, role).queue();
            }
        }
        if (CACHE.get(newBadge) == null) {
            Bot.getBot().sendDebugMessage("autoMod", "removed badge from user `" + member.getEffectiveName() + "` (" + member.getId() + ")`");
            return "Badge removed";
        }
        Role newRole = Bot.getBot().getDeploymentGuild().getRoleById(CACHE.get(newBadge));
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
}
