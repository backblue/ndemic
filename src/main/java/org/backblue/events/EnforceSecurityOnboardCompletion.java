package org.backblue.events;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class EnforceSecurityOnboardCompletion extends ListenerAdapter {

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (event.getGuild().getId().equals(Bot.getBot().getDeploymentGuild().getId())
                && Bot.getBot().getSentinelManager().enforceOnboarding) {
            if (Bot.getBot().getSentinelManager().isEnabled()) {
                Bot.getBot().getScheduler().schedule(()->checkForCompletion(event.getMember().getId()), 20, TimeUnit.MINUTES);
            }
        }
    }

    private void checkForCompletion(String memberID) {
        Member member = Bot.getBot().getDeploymentGuild().getMemberById(memberID);
        if (member != null) {
            Bot.getBot().getSentinelManager().runOnboardingCheck(List.of(member), true);
        }
    }
}
