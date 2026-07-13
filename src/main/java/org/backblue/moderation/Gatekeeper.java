package org.backblue.moderation;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class Gatekeeper extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(Gatekeeper.class);

    final Bot bot;
    final Set<String> joins = new LinkedHashSet<>();
    final Map<String, ScheduledFuture<?>> scheduledChecks;
    final String aiPrompt;
    final Pattern[] regex;
    final String[] susRoles;
    final int minMembersToScan;

    OffsetDateTime lastJoin = OffsetDateTime.MIN;
    OffsetDateTime lastCheck = OffsetDateTime.now();
    int lastCheckAmount = -1;
    long lastCheckMinutes = -1;

    public Gatekeeper(Bot bot, JSONObject json) {
        this.bot = bot;
        bot.getScheduler().scheduleWithFixedDelay(this::runChecks, 30, 20, TimeUnit.MINUTES);

        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper_RequireOnboarding)) {
            scheduledChecks = new ConcurrentHashMap<>();
        } else {
            scheduledChecks = null;
        }

        this.aiPrompt = bot.readResource("genai/gatekeeper.txt");
        if (aiPrompt == null) {
            Log.error("Cannot read internal resource... disabling Gatekeeper");
            bot.disableFeature(FeatureFlag.Gatekeeper);
        }
        if (!bot.isFeatureEnabled(FeatureFlag.AI)) {
            Log.error("Requires feature flag AI, disabling Gatekeeper");
            bot.disableFeature(FeatureFlag.Gatekeeper);
        }
        if (json.optJSONArray("regex") != null) {
            regex = new Pattern[json.optJSONArray("regex").length()];
            for (int i = 0; i < json.optJSONArray("regex").length(); i++) {
                regex[i] = Pattern.compile(json.optJSONArray("regex").getString(i));
            }
        } else {
            Log.error("Cannot find regex array in config... disabling Gatekeeper");
            bot.disableFeature(FeatureFlag.Gatekeeper);
            regex = null;
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

    }

    @Override
    public void onGuildMemberJoin(@NonNull GuildMemberJoinEvent event) {
        joins.add(event.getUser().getId());
        lastJoin = OffsetDateTime.now();
        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper_RequireOnboarding)) {
            int timer = (int) (Math.random() * 40 + 20);
            ScheduledFuture<?> task = scheduledChecks.put(event.getUser().getId(), bot.getScheduler().schedule(() -> {
                this.kickNonCompliance(event.getMember().getId(), timer);
                scheduledChecks.remove(event.getUser().getId());
            }, timer, TimeUnit.MINUTES));
            if (task != null) task.cancel(true);
        }

    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        joins.remove(event.getUser().getId());
        ScheduledFuture<?> task = scheduledChecks.remove(event.getUser().getId());
        if (task != null) task.cancel(true);
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
            boolean ping = jsonResponse.optBoolean("modPings");
            Log.info(jsonResponse.toString(4));
            List<String> list = captured.toList().stream().map(Object::toString).toList();
            bot.getIO().send(DefinedChannel.DeploymentBotCommands, "", bot.getInteractive().createGatekeeper(list, minutes, ping));
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
        if (this.lastJoin.isAfter(OffsetDateTime.now().plusMinutes(10))) {
            Log.info("Did not run checks b/c join in last 10 mins");
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

    public void kickNonCompliance(String id, int time) {
        Member m = bot.getDeploymentGuild().getMemberById(id);
        if (m == null || m.hasPermission(Permission.ADMINISTRATOR)) return;
        if (m.getFlags().contains(Member.MemberFlag.COMPLETED_ONBOARDING)) return;
        if (m.getFlags().contains(Member.MemberFlag.BYPASSES_VERIFICATION)) return;
        m.kick().reason(String.format("Did not complete discord onboarding in %d minutes", time)).queue();
    }
}
