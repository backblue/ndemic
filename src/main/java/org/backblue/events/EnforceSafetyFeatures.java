package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.events.jobs.ProfileScanJob;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class EnforceSafetyFeatures extends ListenerAdapter {

    @Override
    public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {
        runFromEvent(event.getMember(), "guild-newAvatar");
    }

    @Override
    public void onUserUpdateAvatar(@NotNull UserUpdateAvatarEvent event) {
        Member member = Objects.requireNonNull(event.getJDA().getGuildById(Core.DEPLOYMENT.get("guild"))).getMember(event.getUser());
        runFromEvent(member, "global-newAvatar");
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        runFromEvent(event.getMember(), "join");
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        runFromEvent(event.getMember(), "slash");
    }

    private static void runFromEvent(Member member, String source) {
        if (Core.MODULES.get("safetyFeatures")) {
            if (member.getUser().getAvatarUrl() == null) {
                return;
            }
            if (Core.SAFETY.getJSONObject("scanProfile").getBoolean("onUserAvatarChange")) {
                if (member.hasPermission(Permission.ADMINISTRATOR)) {
                    return;
                }
                new ProfileScanJob(member.getId(), source);
            }
        }
    }

}
