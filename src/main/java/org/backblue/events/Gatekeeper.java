package org.backblue.events;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Gatekeeper extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(Gatekeeper.class);

    final Bot bot;
    final Set<String> joins = new LinkedHashSet<>();
    final String aiPrompt;

    public Gatekeeper(Bot bot, JSONObject json) {
        this.bot = bot;
        bot.getScheduler().schedule(() -> {
            this.enoughMembers();
        }, 20, TimeUnit.MINUTES);

        this.aiPrompt = bot.readResource("genai/prompt.txt");
        if (aiPrompt == null) {
            Log.error("Cannot read internal resource... disabling Gatekeeper");
            bot.disableFeature(FeatureFlag.Gatekeeper);
        }
    }

    @Override
    public void onGuildMemberJoin(@NonNull GuildMemberJoinEvent event) {
        joins.add(event.getUser().getId());
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        joins.remove(event.getUser().getId());
    }

    public void runChecks() {
        if (!this.enoughMembers()) return;

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
}
