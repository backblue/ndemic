package org.backblue.events;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Gatekeeper extends ListenerAdapter {

    private static final int MIN_SIZE = 16;
    private static final Logger Log = LoggerFactory.getLogger(Gatekeeper.class);

    final Bot bot;
    final Map<String, JoinEntry> joinsCollection;
    final String[] susRoles;
    final Pattern[] susNamePatterns;

    public Gatekeeper(Bot bot, JSONObject json) {
        this.bot = bot;
        this.joinsCollection = new LinkedHashMap<>();
        if (json == null) {
            Log.warn("No gatekeeper configuration");
            bot.disableFeature(FeatureFlag.Gatekeeper);
            susRoles = new String[0];
            susNamePatterns = new Pattern[0];
            return;
        }

        if (json.optJSONArray("untrustedRoles") != null) {
            susRoles = new String[json.getJSONArray("untrustedRoles").length()];
            for (int i = 0; i < json.getJSONArray("untrustedRoles").length(); i++) susRoles[i] = json.getJSONArray("untrustedRoles").getString(i);
        } else {
            susRoles = new String[0];
            Log.warn("Missing untrusted roles. Feature functionality degraded");
        }
        if (json.optJSONArray("regex") != null) {
            susNamePatterns = new Pattern[json.getJSONArray("regex").length()];
            for (int i = 0; i < json.getJSONArray("regex").length(); i++) susNamePatterns[i] = Pattern.compile(json.getJSONArray("regex").getString(i));
        } else {
            susNamePatterns = new Pattern[0];
            Log.warn("Missing regex patterns. Feature functionality degraded");
        }
        bot.getScheduler().schedule(this::check, 20, TimeUnit.MINUTES);
    }

    public void check() {
        if (joinsCollection.size() < Gatekeeper.MIN_SIZE) bot.getScheduler().schedule(this::check, 5, TimeUnit.MINUTES);

        //todo: add logic

        bot.getScheduler().schedule(this::check, 20, TimeUnit.MINUTES);
    }

    @Override
    public void onGuildMemberJoin(@NonNull GuildMemberJoinEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper) && event.getGuild().getId().equals(bot.getDeploymentGuild().getId())) {
            joinsCollection.put(event.getMember().getId(), new JoinEntry(this.bot, event.getMember().getId(), event.getUser().getName(), event.getUser().getTimeCreated(), event.getMember().getTimeCreated()));
            if (joinsCollection.size() >= Gatekeeper.MIN_SIZE*1.25) check();
        }
    }

    @Override
    public void onGuildMemberRemove(@NonNull GuildMemberRemoveEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper)) joinsCollection.remove(event.getUser().getId());
    }

    record JoinEntry(Bot bot, String id, String username, OffsetDateTime discordTimestamp, OffsetDateTime guildTimestamp) {
        public Member toMember() {
            return bot.getDeploymentGuild().getMemberById(id);
        }
    }
}
