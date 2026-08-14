package org.backblue.moderation;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.enums.DefinedChannel;
import org.backblue.enums.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public final class Gatekeeper extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(Gatekeeper.class);
    private static final ScheduledExecutorService Scheduler = Executors.newScheduledThreadPool(1);

    final Bot bot;
    final Set<String> joins = ConcurrentHashMap.newKeySet();
    final Map<String, ScheduledFuture<?>> scheduledChecks;
    final String aiPrompt;
    final String[] susRoles;
    final int minMembersToScan;
    final long stopBeingSus;

    OffsetDateTime lastJoin = OffsetDateTime.MIN;
    OffsetDateTime lastCheck = OffsetDateTime.now();
    int lastCheckAmount = -1;
    long lastCheckMinutes = -1;

    public Gatekeeper(Bot bot, JSONObject json) {
        String tempAiPrompt;
        this.bot = bot;
        Gatekeeper.Scheduler.scheduleWithFixedDelay(this::runChecks, 30, 20, TimeUnit.MINUTES);

        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper_RequireOnboarding)) {
            scheduledChecks = new ConcurrentHashMap<>();
        } else {
            scheduledChecks = null;
        }

        try {
            Log.warn("Using custom Gatekeeper AI. Improper configuration will cause issues!");
            tempAiPrompt = Files.readString(Path.of("data/gatekeeper-override.txt"));
        } catch (Exception e) {
            tempAiPrompt = bot.readResource("genai/gatekeeper.txt");
            if (tempAiPrompt == null) {
                Log.error("Cannot read internal resource... disabling Gatekeeper");
                bot.disableFeature(FeatureFlag.Gatekeeper);
            }
        }

        this.aiPrompt = tempAiPrompt;
        if (!bot.isFeatureEnabled(FeatureFlag.AI)) {
            Log.error("Requires feature flag AI, disabling Gatekeeper");
            bot.disableFeature(FeatureFlag.Gatekeeper);
        }

        if (json.optJSONArray("susRoles") != null) {
            susRoles = new String[json.optJSONArray("susRoles").length()];
            for (int i = 0; i < json.optJSONArray("susRoles").length(); i++) {
                susRoles[i] = json.optJSONArray("susRoles").getString(i);
            }
        } else {
            Log.error("Cannot find susRoles array in config... disabling Gatekeeper");
            bot.disableFeature(FeatureFlag.Gatekeeper);
            susRoles = null;
        }
        this.minMembersToScan = json.optInt("minMembersToScan", 16);
        this.stopBeingSus = json.optLong("stopBeingSus", 30);
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        joins.add(event.getUser().getId());
        lastJoin = OffsetDateTime.now();
        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper_RequireOnboarding)) {
            int timer = (int) (Math.random() * 40 + 20);
            ScheduledFuture<?> task = scheduledChecks.put(event.getUser().getId(), Gatekeeper.Scheduler.schedule(() -> {
                this.kickNonCompliance(event.getMember().getId(), timer);
                this.removeLowQualityAccounts(event.getMember().getId());
                scheduledChecks.remove(event.getUser().getId());
            }, timer, TimeUnit.MINUTES));
            if (task != null) task.cancel(true);
        }

    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        joins.remove(event.getUser().getId());
        if (this.scheduledChecks != null) {
            ScheduledFuture<?> task = scheduledChecks.remove(event.getUser().getId());
            if (task != null) task.cancel(true);
        }

    }

    public void runChecks() {

        if (!this.precheck() || !bot.isFeatureEnabled(FeatureFlag.Gatekeeper)) return;
        JSONObject json = this.asJSON(this.joins);

        long minutes = Math.abs(Duration.between(OffsetDateTime.now(), this.lastCheck).toMinutes());
        Log.info(json.toString());
        GenerateContentConfig config = GenerateContentConfig.builder().temperature(0.0f).responseMimeType("application/json").build();
        String prompt = aiPrompt.replace("{{ACCOUNTS_JSON}}", json.toString());
        prompt = prompt.replace("{{INTERVAL}}", minutes + " minutes");
        prompt = prompt.replace("{MEMBERS_JOINED_LAST_CYCLE}", String.valueOf(this.lastCheckAmount));
        prompt = prompt.replace("{TIME_TOOK_TO_CHECK_THOSE_MEMBERS}", String.valueOf(this.lastCheckMinutes));

        GenerateContentResponse r = bot.getAI().inputString(prompt, config);
        JSONArray captured;
        try {
            if (r == null || r.text() == null) throw new NullPointerException();
            JSONObject jsonResponse = new JSONObject(r.text());
            captured = jsonResponse.optJSONArray("flagged");
            Log.info(jsonResponse.toString(4));
            List<String> list = captured.toList().stream().map(Object::toString).toList();
            if (!list.isEmpty()) bot.getIO().send(DefinedChannel.DeploymentBotCommands, "", bot.getInteractive().createGatekeeper(list, this.lastCheck.toEpochSecond()));
        } catch (JSONException | NullPointerException e) {
            Log.error("Failure to parse AI response: {}", e.getMessage());
            return;
        }
        this.lastCheckAmount = this.joins.size();
        this.lastCheckMinutes = minutes;
        this.lastCheck = OffsetDateTime.now();
        this.joins.clear();
    }

    public boolean precheck() {
        Log.info("{} members queued.", this.joins.size());
        if (this.lastJoin.isAfter(OffsetDateTime.now().minusMinutes(10))) {
            Log.debug("Did not run checks b/c join in last 10 mins");
            return false;
        }
        return this.joins.size() >= this.minMembersToScan;
    }

    public JSONObject asJSON(Set<String> keys) {
        JSONObject json = new JSONObject();
        JSONArray arr = new JSONArray();
        for (String key : keys) {
            Member m = bot.getDeploymentGuild().getMemberById(key);
            if (m != null) {
                JSONObject obj = new JSONObject();
                obj.put("id", m.getId());
                obj.put("username", m.getUser().getName());
                obj.put("displayName", m.getEffectiveName());
                obj.put("discordCreatedTimestamp", m.getUser().getTimeCreated().toEpochSecond());
                obj.put("guildCreatedTimestamp", m.getTimeJoined().toEpochSecond());
                arr.put(obj);
            }
        }
        json.put("members", arr);
        return json;
    }

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {
        if (event.getModalId().equals("modal:gatekeeper")) {
            List<String> targets = event.getValues().getFirst().getAsStringList();
            boolean ban = event.getValues().get(1).getAsString().equals("ban");
            event.reply(ban ? "Banned" : "Softbanned" + " **" + targets.size() + "** members").setEphemeral(true).queue();
            for (String target : targets) {
                Member m = bot.getDeploymentGuild().getMemberById(target);
                if (m == null || m.hasPermission(Permission.ADMINISTRATOR)) continue;
                m.ban(0, TimeUnit.SECONDS).queue(
                        success -> {
                            if (!ban) bot.getDeploymentGuild().unban(m).queue();
                        },
                        failure -> {}
                );
            }
        }
    }

    public void kickNonCompliance(String id, int time) {
        Member m = bot.getDeploymentGuild().getMemberById(id);
        if (m == null || m.hasPermission(Permission.ADMINISTRATOR)) return;
        if (m.getFlags().contains(Member.MemberFlag.COMPLETED_ONBOARDING)) return;
        if (m.getFlags().contains(Member.MemberFlag.BYPASSES_VERIFICATION)) return;
        m.kick().reason(String.format("Did not complete discord onboarding in %d minutes", time)).queue();
    }

    public void removeLowQualityAccounts(String id) {
        if (!bot.isFeatureEnabled(FeatureFlag.Gatekeeper_RemoveLowQualityAccounts)) return;
        Member m = bot.getDeploymentGuild().getMemberById(id);
        if (m == null || m.hasPermission(Permission.ADMINISTRATOR)) return;
        Set<Role> susRoles = new HashSet<>();
        for (String susRole : this.susRoles) {
            if (bot.getDeploymentGuild().getRoleById(susRole) != null) {
                susRoles.add(bot.getDeploymentGuild().getRoleById(susRole));
            }
        }
        if (susRoles.isEmpty() || Collections.disjoint(susRoles, m.getRoles())) return;
        long timeDifference = Math.abs(ChronoUnit.SECONDS.between(m.getUser().getTimeCreated(), OffsetDateTime.now()));
        if (timeDifference < this.stopBeingSus * 24 * 60 *60) {
            m.kick().reason("Joined too quickly after account creation -- " + bot.formattedTime(timeDifference, false)).queue();
        }
    }
}
