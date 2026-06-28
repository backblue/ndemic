package org.backblue.moderation;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Gatekeeper extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(Gatekeeper.class);

    final Bot bot;
    final Set<String> joins = new LinkedHashSet<>();
    final Map<String, ScheduledFuture<?>> scheduledTasks;
    final String aiPrompt;

    final Pattern[] regex;
    final String[] susRoles;

    public Gatekeeper(Bot bot, JSONObject json) {
        this.bot = bot;
        bot.getScheduler().schedule(this::runChecks, 20, TimeUnit.MINUTES);

        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper_RequireOnboarding)) {
            scheduledTasks = new ConcurrentHashMap<>();
        } else {
            scheduledTasks = null;
        }

        this.aiPrompt = bot.readResource("genai/prompt.txt");
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
    }

    @Override
    public void onGuildMemberJoin(@NonNull GuildMemberJoinEvent event) {
        joins.add(event.getUser().getId());
        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper_RequireOnboarding)) {
            int timer = (int) (Math.random() * 22 + 8);
            ScheduledFuture<?> task = scheduledTasks.put(event.getUser().getId(), bot.getScheduler().schedule(() -> {
                this.kickNonCompliance(event.getMember().getId(), timer);
                scheduledTasks.remove(event.getUser().getId());
            }, timer, TimeUnit.MINUTES));
            if (task != null) task.cancel(true);
        }

    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        joins.remove(event.getUser().getId());
        ScheduledFuture<?> task = scheduledTasks.remove(event.getUser().getId());
        if (task != null) task.cancel(true);
    }

    public void runChecks() {
        if (!this.enoughMembers() && !bot.isFeatureEnabled(FeatureFlag.Gatekeeper)) return;
        JSONObject json = this.asJSON(this.joins);
        GenerateContentConfig config = GenerateContentConfig.builder().temperature(0.0f).responseMimeType("application/json").build();
        GenerateContentResponse r = bot.getAI().inputString(aiPrompt.replace("{{ACCOUNTS_JSON}}", json.toString()), config);
        JSONArray captured;
        bot.getIO().send(DefinedChannel.DebugDirectMessages, "A check is ran <@852609613253443584>");
        try {
            if (r == null || r.text() == null) throw new NullPointerException();
            captured = new JSONObject(r.text()).optJSONArray("captured");
            Log.info(captured.toString());
        } catch (JSONException | NullPointerException e) {
            Log.error("Failure to parse AI response: {}", e.getMessage());
            return;
        }


        this.joins.clear();
    }

    public boolean enoughMembers() {
        if (joins.size() >= 16) return true;
        if (joins.size() >= 18 * 0.8) {
            bot.getScheduler().schedule(() -> {
                this.enoughMembers();
            }, 5, TimeUnit.MINUTES);
        } else {
            bot.getScheduler().schedule(() -> {
                this.enoughMembers();
            }, 20, TimeUnit.MINUTES);
        }
        return false;
    }

    public JSONObject asJSON(Set<String> keys) {
        JSONObject json = new JSONObject();
        for (String key : keys) {
            Member m = bot.getDeploymentGuild().getMemberById(key);
            if (m != null) {
                JSONObject obj = new JSONObject();
                obj.put("id", m.getId());
                obj.put("username", m.getUser().getName());
                obj.put("displayName", m.getEffectiveName());
                obj.put("discordCreatedTimestamp", m.getUser().getTimeCreated().toEpochSecond());
                obj.put("guildCreatedTimestamp", m.getTimeJoined().toEpochSecond());
                json.put(key, obj);
            }
        }
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
