package org.backblue.events;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class Gatekeeper extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(Gatekeeper.class);

    final Bot bot;
    final Map<String, JoinEntry> joinsCollection = new LinkedHashMap<>();

    public Gatekeeper(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onGuildMemberJoin(@NonNull GuildMemberJoinEvent event) {
        if (bot.isFeatureEnabled(FeatureFlag.Gatekeeper) && event.getGuild().getId().equals(bot.getDeploymentGuild().getId())) {
            joinsCollection.put(event.getMember().getId(), new JoinEntry(this.bot, event.getMember().getId(), event.getUser().getName(), event.getUser().getTimeCreated(), event.getMember().getTimeCreated()));
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
