package org.backblue.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.enums.AuditAction;
import org.backblue.enums.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Audit Log replacement:
 * <br>
 * - Guild Member Ban Updates<br>
 * - Guild Member Roles Updates<br>
 * - Guild Member Join/Exit Updates<br>
 * <br>
 * See {@link org.backblue.core.MessageIO} for Message Edits/Deletions
 */
public class Auditing extends ListenerAdapter {

    static final Logger Log = LoggerFactory.getLogger(Auditing.class);

    final Bot bot;
    final JSONObject deploymentAuditFile;
    String url;
    WebhookClient<Message> webhookClient;
    EnumSet<AuditAction> listeners;

    public Auditing(Bot bot, JSONObject deploymentAuditFile) {
        this.bot = bot;
        this.deploymentAuditFile = deploymentAuditFile;
    }

    private void setup() {
        String tempURL;
        WebhookClient<Message> tempWebhook;
        listeners = EnumSet.noneOf(AuditAction.class);
        if (deploymentAuditFile == null) {
            webhookClient = null;
            url = null;
            bot.disableFeature(FeatureFlag.Audit);
            Log.warn("Did not find an audit file to read off from");
            return;
        }
        for (AuditAction action : AuditAction.values()) {
            if (deploymentAuditFile.getBoolean(action.configKey())) {
                listeners.add(action);
            }
        }
        @NotNull String url = deploymentAuditFile.optString("webhookLink", "");
        try {
            tempURL = url;
            tempWebhook = WebhookClient.createClient(bot.getJDA().getShards().getFirst(), url);
            Log.info("Webhook for audits active");
        } catch (IllegalArgumentException e) {
            tempURL = null;
            tempWebhook = null;
            Log.error("Invalid Webhook URL, cannot send audits");
            bot.disableFeature(FeatureFlag.Audit);
        }
        this.url = tempURL;
        webhookClient = tempWebhook;
        bot.getIO().assignAuditing(this);
    }


    @Override
    public void onReady(@NonNull ReadyEvent event) {
        this.setup();
    }

    public String webhookURL() {
        return url;
    }
    public boolean has(AuditAction action) {
        return listeners.contains(action);
    }
    public void toggle(AuditAction feature) {
        if (listeners.contains(feature)) {
            listeners.remove(feature);
            return;
        }
        listeners.add(feature);
    }

    @Override
    public void onGuildBan(@NonNull GuildBanEvent event) {
        if (event.getGuild().getId().equals(bot.getDeploymentGuild().getId())
        && listeners.contains(AuditAction.MembersBan)) {
            EmbedBuilder embedBuilder = this.base(event.getUser());
            embedBuilder.setAuthor("Member Banned", event.getUser().getAvatarUrl(), event.getUser().getAvatarUrl());
            embedBuilder.setDescription(event.getUser().getAsMention() + " " + event.getUser().getName());
            embedBuilder.setColor(Color.RED);
            this.sendAudit(embedBuilder.build());
        }
    }

    @Override
    public void onGuildUnban(@NonNull GuildUnbanEvent event) {
        if (event.getGuild().getId().equals(bot.getDeploymentGuild().getId())
                && listeners.contains(AuditAction.MembersUnban)) {
            EmbedBuilder embedBuilder = this.base(event.getUser());
            embedBuilder.setAuthor("Member Unbanned", event.getUser().getAvatarUrl(), event.getUser().getAvatarUrl());
            embedBuilder.setDescription(event.getUser().getAsMention() + " " + event.getUser().getName());
            embedBuilder.setColor(Color.CYAN);
            this.sendAudit(embedBuilder.build());
        }
    }

    @Override
    public void onGuildMemberRoleAdd(@NonNull GuildMemberRoleAddEvent event) {
        memberChangeRoles(event.getGuild(), event.getRoles(), event.getUser());
    }

    @Override
    public void onGuildMemberRoleRemove(@NonNull GuildMemberRoleRemoveEvent event) {
        memberChangeRoles(event.getGuild(), event.getRoles(), event.getUser());
    }

    private void memberChangeRoles(Guild guild, List<Role> roles, User user) {
        if (guild.getId().equals(bot.getDeploymentGuild().getId())
                && listeners.contains(AuditAction.MembersRoleAdd) && !roles.isEmpty()) {
            StringBuilder b = new StringBuilder();
            roles.forEach(role -> b.append("`").append(role.getName()).append("`").append(" ,"));
            b.deleteCharAt(b.length() - 1).deleteCharAt(b.length() - 1);
            EmbedBuilder embedBuilder = this.base(user);
            embedBuilder.setColor(Color.CYAN);
            embedBuilder.setDescription(user.getAsMention() + " removed roles: **" + b + "**");
            this.sendAudit(embedBuilder.build());
        }
    }

    @Override
    public void onGuildMemberJoin(@NonNull GuildMemberJoinEvent event) {
        if (event.getGuild().getId().equals(bot.getDeploymentGuild().getId())
                && listeners.contains(AuditAction.MemberJoin)) {
            EmbedBuilder embedBuilder = this.base(event.getUser());
            embedBuilder.setColor(Color.CYAN);
            embedBuilder.setAuthor("Member Joined", event.getUser().getAvatarUrl(), event.getUser().getAvatarUrl());
            embedBuilder.setDescription(event.getUser().getAsMention() + " " + event.getUser().getName());
            embedBuilder.addField("Account Age", bot.formattedTime(event.getUser().getTimeCreated().toEpochSecond(), false), false);
            this.sendAudit(embedBuilder.build());
        }
    }

    @Override
    public void onGuildMemberRemove(@NonNull GuildMemberRemoveEvent event) {
        if (event.getGuild().getId().equals(bot.getDeploymentGuild().getId())
                && listeners.contains(AuditAction.MembersRoleRemove)) {
            EmbedBuilder embedBuilder = this.base(event.getUser());
            embedBuilder.setColor(Color.RED);
            embedBuilder.setAuthor("Member Left", event.getUser().getAvatarUrl(), event.getUser().getAvatarUrl());
            embedBuilder.setDescription(event.getUser().getAsMention() + " " + event.getUser().getName());
            this.sendAudit(embedBuilder.build());
        }
    }

    public void sendAudit(MessageEmbed message) {
        this.webhookClient.sendMessageEmbeds(message)
                .setUsername(bot.getJDA().getShards().getFirst().getSelfUser().getName())
                .setAvatarUrl(bot.getJDA().getShards().getFirst().getSelfUser().getAvatarUrl())
                .queue();
    }

    public EmbedBuilder base(User user) {
        return new EmbedBuilder()
                .setAuthor(user.getName(), user.getEffectiveAvatarUrl(), user.getEffectiveAvatarUrl())
                .setFooter("ID: " + user.getId())
                .setTimestamp(OffsetDateTime.now());
    }
}
