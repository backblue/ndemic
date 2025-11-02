package org.backblue.events;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.tasks.ProfileScanTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class EnforceProfileScan extends ListenerAdapter {

    @Override
    public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {
        runFromEvent(event.getMember(), "guildAvatarChange");
    }

    @Override
    public void onUserUpdateAvatar(@NotNull UserUpdateAvatarEvent event) {
        Member member = Objects.requireNonNull(Objects.requireNonNull(event.getJDA().getGuildById(Bot.getBot().getDeployment().get("guild"))).getMember(event.getUser()));
        runFromEvent(member, "userAvatarChange");
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        runFromEvent(event.getMember(), "join");
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        runFromEvent(event.getMember(), "slash");
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        runFromEvent(event.getMember(), "msg");
    }

    private static void runFromEvent(Member member, String source) {
        if (Bot.getBot().getModuleValue("profileScanning")) {
            if (member == null) {
                return;
            }
            if (Bot.getBot().getTasks().getJSONObject("profileScanning").getBoolean(source)) {
                if (member.getUser().getAvatarUrl() == null || member.hasPermission(Permission.ADMINISTRATOR)) {
                    return;
                }
                new ProfileScanTask(member.getUser().getId(), source);

            }
        }
    }

}