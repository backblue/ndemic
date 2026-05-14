package org.backblue.wrappers;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.backblue.Bot;
import org.backblue.utilities.NdemicModule;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class SentinelManager implements NdemicModule {

    public final List<String> interestedRoles;
    private int randomChecksDone = 0;
    private int punishmentsDone = 0;
    private int minutesSincePunishmentReport = 1;
    private int securityLevel = 0;
    private final Map<String, Stack<OffsetDateTime>> kickedThenRejoined = new HashMap<>();

    private final List<String> regexHighSeverity = new ArrayList<>();

    public final boolean supervised;
    public final boolean enforceOnboarding;
    public final boolean randomChecks;
    public final boolean gatekeeper;

    public SentinelManager(JSONObject json) {
        interestedRoles = new ArrayList<>();
        try {
            for (int i = 0; i < json.getJSONArray("interestedRoles").length() && json.getJSONArray("interestedRoles").get(i) instanceof String; i++) {
                interestedRoles.add(json.getJSONArray("interestedRoles").getString(i));
            }
            enforceOnboarding = json.getBoolean("requireOnboardCompletion");
            randomChecks = json.getBoolean("randomChecks");
            gatekeeper = json.getBoolean("gatekeeper");
            supervised = json.getBoolean("supervised");
            regexHighSeverity.addAll(json.optJSONArray("regexHighSeverity").toList().stream().filter(o -> o instanceof String).map(o -> (String) o).toList());

            if (randomChecks) Bot.getBot().getScheduler().schedule(this::runCheck, this.minutesSincePunishmentReport, TimeUnit.MINUTES);
            Bot.getBot().getScheduler().scheduleAtFixedRate(this::dec, 30, 30, TimeUnit.MINUTES);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to load SentinelManager due to invalid or missing configuration.", e);
        }

    }

    @Override
    public String name() {
        return "sentinel";
    }

    private void dec() {
        this.securityLevel = (securityLevel > 0) ? securityLevel - 1 : 0;
    }

    private void runCheck() {
        randomChecksDone++;
        int time = (int) (Math.random() * 20) + 10;
        Bot.getBot().getScheduler().schedule(this::runCheck, time, TimeUnit.MINUTES);
        minutesSincePunishmentReport +=time;

        List<Member> members = pullRandomMembers();
        if (members.isEmpty()) return;
        if (enforceOnboarding) runOnboardingCheck(members, false);
        if (gatekeeper) runTrivialChecks(members);
        if (punishmentsDone > 0 && randomChecksDone % 3 == 0) {
            Bot.getBot().sendDeploymentMessage("log", "Removed potential **" +  punishmentsDone + "** scam/bot members in the last " + minutesSincePunishmentReport + " minutes.");
            punishmentsDone = 0;
            minutesSincePunishmentReport = 0;
        }
    }

    private void kickCheck(String memberId) {
        kickedThenRejoined.computeIfAbsent(memberId, k -> new Stack<>()).push(OffsetDateTime.now());
        if ((kickedThenRejoined.get(memberId).size() % 3) == 0) {
            long totalMillis = 0;
            int count = 0;
            for (int i = kickedThenRejoined.get(memberId).size() - 1; i > 0; i--) {
                OffsetDateTime newer = kickedThenRejoined.get(memberId).get(i);
                OffsetDateTime older = kickedThenRejoined.get(memberId).get(i - 1);
                Duration diff = Duration.between(older, newer);

                totalMillis += diff.toMillis();
                count++;
            }
            Duration average = Duration.ofMillis(totalMillis / count);
            if (average.toHours() < 3) {
                StringBuilder str =  new StringBuilder("@everyone Potential spam attack? There's a user that joined/rejoined " + kickedThenRejoined.get(memberId).size() + " in a 3 hr timespan <#558690605698514994>"
                        + "\n" + "Average time between joins: " + average.toMinutes() + " minutes.");
                str.append("Kicked intervals:\n");
                for (int i = 0; i < kickedThenRejoined.get(memberId).size() - 1; i++) {
                    str.append("- <t:").append(kickedThenRejoined.get(memberId).get(i).toEpochSecond()).append(":S>").append("\n");
                }
                Bot.getBot().sendDebugMessage("autoMod", str.toString());
            }
        }
    }

    private List<Member> pullRandomMembers() {
        try {
            List<Member> members = Bot.getBot().getDeploymentGuild().loadMembers().get();

            List<Member> list = new ArrayList<>(members);
            Collections.shuffle(list);

            return list.stream()
                    .limit(24)
                    .toList();

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
    public void runOnboardingCheck(List<Member> members, boolean fromJoinEvent) {
        for (Member member : members) {
            if (Math.abs(Duration.between(OffsetDateTime.now(), member.getTimeJoined()).toMinutes()) < 20) {
                continue;
            }
            EnumSet<Member.MemberFlag> flags = member.getFlags();
            if (flags.isEmpty()) {
                continue;
            }
            if (flags.contains(Member.MemberFlag.COMPLETED_ONBOARDING)) {
                continue;
            }
            if (!runRegexChecks(member) && flags.contains(Member.MemberFlag.STARTED_ONBOARDING)) {
                punishmentsDone++;
                kickCheck(member.getId());
                securityLevel++;
                if (!supervised) {
                    member.kick().reason(fromJoinEvent ? "Failure to onboard after join" : "Failure to onboard at all after joining").queue();
                } else {
                    Bot.getBot().sendDeploymentMessage("log", "Supervision: " + member.getAsMention() + " would've been kicked-failure to complete onboarding in a reasonable timespan.");
                }
                if (fromJoinEvent) {
                    Bot.getBot().sendDeploymentMessage("log", "Kicked " + member.getAsMention() + " for failure to complete onboarding in a reasonable timespan.");
                }
            }
        }
    }
    public void runTrivialChecks(List<Member> members) {
        List<Role> roles = new ArrayList<>();
        for (String role : interestedRoles) {
            Role n = Bot.getBot().getJDA().getRoleById(role);
            if (n != null) roles.add(Bot.getBot().getJDA().getRoleById(role));
        }

        int delay = (int) (Math.random() * 10) + 30;
        for (Member member : members) {
            Duration creationToJoin = Duration.between(member.getUser().getTimeCreated(), member.getTimeJoined());
            if (runRegexChecks(member)) {
                return;
            }
            if (creationToJoin.toHours() < 240 + securityLevel * 2L && member.getRoles().stream().anyMatch(roles::contains)) {
                punishmentsDone++;
                securityLevel++;
                delay += (int) (Math.random() * 10) + 25;
                Bot.getBot().getScheduler().schedule(() -> {
                    kickCheck(member.getId());
                    member.kick().reason("Difference between account creation and joining too short (" + creationToJoin.toHours() + " hours) AND has 'interesting' roles").queue();
                }, delay, TimeUnit.SECONDS);
            }
        }
    }

    public boolean runRegexChecks(Member member) {
        int delay = (int) (Math.random() * 8) + 36;
        for (String regex : this.regexHighSeverity) {
            if (Pattern.matches(regex, member.getEffectiveName())) {
                punishmentsDone++;
                securityLevel++;
                delay += (int) (Math.random() * 6) + 11;
                Bot.getBot().getScheduler().schedule(() -> {
                    kickCheck(member.getId());
                    member.kick().reason("Failed high-severity username regex check, high probable chance of spambot").queue();
                    Bot.getBot().sendDeploymentMessage("log", "Kicked " + member.getAsMention() + ": matched high-severity regex check");
                }, delay, TimeUnit.SECONDS);
                return true;
            }
        }

        return false;
    }

}
