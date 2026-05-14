package org.backblue.events;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateFlagsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.wrappers.SentinelManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

public class EnforceSecurityGatekeeper extends ListenerAdapter {

    private final Map<String, OffsetDateTime> interestingRoleChecks = new HashMap<>();
    private final Set<String> interestingRoles;
    private final Set<MemberJoinToJSON> tobeAnalyzed = new HashSet<>();
    private OffsetDateTime nextAnalysis;

    public EnforceSecurityGatekeeper(SentinelManager sentinelManager) {
        this.interestingRoles = new HashSet<>(sentinelManager.interestedRoles);
        nextAnalysis = OffsetDateTime.now().plusHours(2);
    }

    @Override
    public void onGuildMemberUpdateFlags(@NotNull GuildMemberUpdateFlagsEvent event) {

        if (event.getGuild().getId().equals(Bot.getBot().getDeploymentGuild().getId())
                && Bot.getBot().getSentinelManager().isEnabled()
                && Bot.getBot().getSentinelManager().gatekeeper) {
                Bot.getBot().getSentinelManager().runTrivialChecks(List.of(event.getMember()));
        }
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        if (event.getGuild().getId().equals(Bot.getBot().getDeploymentGuild().getId())
                && Bot.getBot().getSentinelManager().isEnabled()
                && Bot.getBot().getSentinelManager().gatekeeper) {
            this.removeOlderChecks();
            if (!Collections.disjoint(this.interestingRoles, event.getMember().getRoles())) {
                interestingRoleChecks.put(event.getMember().getId(), OffsetDateTime.now());
            }
            int addedRoleLifespan = 5;
            if (interestingRoleChecks.size() > addedRoleLifespan) {
                Bot.getBot().sendDebugMessage("autoMod", "@everyone Potential bot attack? <#558690605698514994>");
                interestingRoleChecks.clear();
            }
            tobeAnalyzed.add(new MemberJoinToJSON(event.getMember().getId(), event.getMember().getUser().getName(), event.getMember().getTimeJoined().toEpochSecond(), event.getMember().getTimeCreated().toEpochSecond()));

        }
    }

    private void removeOlderChecks() {
        for (String role : interestingRoleChecks.keySet()) {
            int addedRoleSize = 3;
            if (Duration.between(interestingRoleChecks.get(role), OffsetDateTime.now()).toMinutes() >= addedRoleSize) {
                interestingRoleChecks.remove(role);
            }
        }
    }

    private void analyze(MemberJoinToJSON n) {
        tobeAnalyzed.add(n);
        if (tobeAnalyzed.size() > 30) {
            nextAnalysis = OffsetDateTime.now().plusHours(2);
        } else if (OffsetDateTime.now().isAfter(nextAnalysis) && tobeAnalyzed.size() < 20) {
            nextAnalysis = OffsetDateTime.now().plusHours(1);
            return;
        } else if (OffsetDateTime.now().isAfter(nextAnalysis) && tobeAnalyzed.size() < 10) {
            nextAnalysis = OffsetDateTime.now().plusHours(2);
            return;
        }
        JSONObject json = memberJSON(tobeAnalyzed);
        GenerateContentConfig config = GenerateContentConfig.builder().responseMimeType("application/json").temperature(0.1f).build();
        //todo: run analysis on json
        String prompt = "";
        GenerateContentResponse response = Bot.getBot().gemini(prompt, config);
        if (response == null) {
            nextAnalysis = OffsetDateTime.now().plusMinutes(5);
        }
    }

    private record MemberJoinToJSON(String id, String userName, long joinTimeStamp, long createdTimeStamp) {}
    private JSONObject memberJSON(Set<MemberJoinToJSON> m) {
        JSONObject obj = new JSONObject();
        for (MemberJoinToJSON member : m) {
            if (Bot.getBot().getDeploymentGuild().getMemberById(member.id) == null) continue;
            obj.put(member.id, new JSONObject().put("username", member.userName).put("joinTimeStamp", member.joinTimeStamp).put("createdTimeStamp", member.createdTimeStamp));
        }
        m.clear();
        return obj;
    }
}
