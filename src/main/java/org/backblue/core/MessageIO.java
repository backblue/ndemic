package org.backblue.core;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.cloud.CryptoDetection;
import org.backblue.enums.AuditAction;
import org.backblue.enums.LiveFramework;
import org.backblue.enums.DefinedChannel;
import org.backblue.moderation.Auditing;
import org.backblue.moderation.OneAuthorGuide;
import org.backblue.moderation.Forwarding;
import org.backblue.moderation.Honeypot;
import org.backblue.utilities.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public final class MessageIO extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(MessageIO.class);
    private @Nullable Auditing auditing;

    private final Bot bot;
    private final Map<DefinedChannel, String> definedChannels;
    private final PriorityQueue<MessagePriority> messageListenersPriority = new PriorityQueue<>();

    private final Map<String, Deque<Message>> recentMessages = new ConcurrentHashMap<>();
    private final Map<Long, Message> recentMessageIds = new ConcurrentHashMap<>();
    private final Map<String, Long> userLastActivity = new ConcurrentHashMap<>();
    private final int prevMessagesLoggingLimit;
    private final long inactiveTimeoutHours;

    public void send(DefinedChannel dest, String text) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(definedChannels.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
    }

    public void send(DefinedChannel dest, String text, MessageEmbed embed) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(definedChannels.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).setEmbeds(embed).queue();
    }

    public void send(DefinedChannel dest, String text, MessageEmbed embed, List<FileUpload> fileUploads) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(definedChannels.get(dest));
        if (embed == null && targetChannel instanceof MessageChannelUnion messageChannel) {
            messageChannel.sendMessage(text).addFiles(fileUploads).queue();
            return;
        }
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).addFiles(fileUploads).setEmbeds(embed).queue();
    }

    public void send(DefinedChannel dest, String text, List<FileUpload> fileUploads) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(definedChannels.get(dest));
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).addFiles(fileUploads).queue();
    }

    public void send(User user, String text) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(text).queue(
                success -> {},
                failure -> {}
        ));
    }

    public void send(User user, String text, MessageEmbed embed) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(text).setEmbeds(embed).queue(
                success -> {},
                failure -> {}
        ));
    }

    public void send(String textChannelID, String text) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(textChannelID);
        if (targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
    }

    public void send(DefinedChannel dest, String text, Container container, LiveFramework handler) {
        GuildChannel targetChannel = this.bot.getJDA().getGuildChannelById(definedChannels.get(dest));
        if (text != null && !text.isEmpty() && targetChannel instanceof MessageChannelUnion messageChannel) messageChannel.sendMessage(text).queue();
        if (targetChannel instanceof MessageChannelUnion messageChannel) {
            if (handler != null) messageChannel.sendMessageComponents(container).useComponentsV2(true).queue();
            else messageChannel.sendMessageComponents(container).useComponentsV2(true).queue(
                    msg -> bot.getLiveContainer().applyContainerization(container, msg, handler));
        }
    }

    public GuildChannel getChannel(DefinedChannel dest) {
        return this.bot.getJDA().getTextChannelById(definedChannels.get(dest));
    }
    public void clean(String id) {
        Deque<Message> messages = this.recentMessages.remove(id);
        if (messages == null || messages.isEmpty()) return;

        for (Message message : messages) {
            message.delete().queue(
                    success -> {},
                    failure -> {
                        if (failure instanceof ErrorResponseException f && f.getErrorCode() != 10008) {
                            Log.error("Unhandled error: ", failure);
                        }
                    }
            );
        }
    }

    public MessageIO(JSONObject settings, Bot bot, Properties props) {
        this.bot = bot;
        this.definedChannels = new EnumMap<>(DefinedChannel.class);
        JSONObject settingsChannel = settings.optJSONObject("channels", null);

        if (settingsChannel == null) {
            Log.error("Missing \"channels\" key-object in config. Non-interaction responses will not be sent.");
            prevMessagesLoggingLimit = -1;
            inactiveTimeoutHours = -1;
            return;
        }
        for (DefinedChannel channel : DefinedChannel.values()) {
            try {
                definedChannels.put(channel, settingsChannel.getString(channel.configKey()));
            } catch (JSONException e) {
                definedChannels.put(channel, null);
                Log.error("Key '{}' unable to be read. Messages to that channel will not be sent", channel.configKey());
            }
        }

        prevMessagesLoggingLimit = settingsChannel.optInt("_track", 8);
        inactiveTimeoutHours = settingsChannel.optInt("_timeOutHrs", 1);

        messageListenersPriority.add(new OneAuthorGuide(50, bot));
        messageListenersPriority.add(new Honeypot(5, bot));
        messageListenersPriority.add(new Forwarding(15, bot, settings.optJSONArray("messageForwarding")));
        messageListenersPriority.add(new CryptoDetection(6, bot, settings.optJSONObject("detectCrypto"), (String) props.getOrDefault("VISION_ENDPOINT", null), (String) props.getOrDefault("VISION_KEY", null)));
        bot.getScheduler().scheduleAtFixedRate(this::cleanupInactiveUsers, 1, 1, TimeUnit.HOURS);
    }

    public void assignAuditing(Auditing auditing) {
        if (auditing != null) this.auditing = auditing;
    }

    private void cleanupInactiveUsers() {
        long now = System.currentTimeMillis();
        userLastActivity.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > inactiveTimeoutHours * 60 * 1000) {
                recentMessages.remove(entry.getKey());
                return true;
            }
            return false;
        });
        Log.info("Cleaned up inactive users. Storing {} users", recentMessages.size());
    }

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.isFromGuild() && event.getGuild().getId().equals(bot.getDeploymentGuild().getId()) && !event.getAuthor().isBot()) {
            for (MessagePriority listener : messageListenersPriority) if (listener.cancelled(event)) return;
            if (event.getMember() == null) return;
            String userId = event.getAuthor().getId();
            Deque<Message> messages = this.recentMessages.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
            messages.addLast(event.getMessage());
            if (messages.size() >= this.prevMessagesLoggingLimit) {
                messages.removeFirst();
            }
            userLastActivity.put(userId, System.currentTimeMillis());
            recentMessageIds.put(event.getMessageIdLong(), event.getMessage());
        }
    }

    private String textJump(Message message) {
        return "https://discord.com/channels/" + message.getGuildId() + "/" + message.getChannelId() + "/" + message.getId();
    }

    @Override
    public void onMessageUpdate(@NonNull MessageUpdateEvent event) {
        if (event.isFromGuild()
                && event.getGuild().getId().equals(bot.getDeploymentGuild().getId())
                && this.auditing != null
                && !event.getAuthor().isBot()
                && auditing.has(AuditAction.MessageEdit)) {
            Message msg = null;
            Deque<Message> messages =  this.recentMessages.computeIfAbsent(event.getAuthor().getId(), id -> new ConcurrentLinkedDeque<>());
            for (Message message : messages) {
                if (message.getId().equals(event.getMessageId())) {
                    msg = message;
                    break;
                }
            }

            EmbedBuilder embed = auditing.base(event.getAuthor());
            embed.addField("Old", msg != null ? msg.getContentRaw() : "*[Irretrievable message]*", false);
            embed.addField("New", event.getMessage().getContentRaw(), false);
            embed.addField("Jump to", textJump(event.getMessage()), false);
            embed.setColor(Color.RED);
            embed.setDescription(event.getAuthor().getAsMention() + " **edited a message in " + event.getChannel().getAsMention() + "**");
            auditing.sendAudit(embed.build());
            this.recentMessageIds.put(event.getMessageIdLong(), event.getMessage());
        }
    }

    @Override
    public void onMessageDelete(@NonNull MessageDeleteEvent event) {
        if (event.isFromGuild()
                && event.getGuild().getId().equals(bot.getDeploymentGuild().getId())
                && this.auditing != null
                && auditing.has(AuditAction.MessageDelete)) {

            Message msg = this.recentMessageIds.get(event.getMessageIdLong());
            if (msg != null && !msg.getAuthor().isBot() && msg.getMember() != null) {
                User member = msg.getMember().getUser();
                EmbedBuilder embed = auditing.base(member);
                embed.addField("Content", msg.getContentRaw(), false);
                embed.setColor(Color.RED);
                embed.setDescription(member.getAsMention() + " **deleted a message in " + event.getChannel().getAsMention() + "**");
                auditing.sendAudit(embed.build());
            }
        }
    }
}
