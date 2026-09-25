package org.backblue.moderation;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.backblue.core.Bot;
import org.backblue.enums.SetChannel;
import org.backblue.enums.Feature;
import org.backblue.extension.LiveFramework;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public final class Gatekeeper extends ListenerAdapter implements LiveFramework.ButtonReturn {

    private static final Logger Log = LoggerFactory.getLogger(Gatekeeper.class);
    private static final ScheduledExecutorService Scheduler = Executors.newScheduledThreadPool(1);
    private static final int MAX_BATCH_CACHE_SIZE = 16;
    private static final int FOOTER_NOTE = 101;

    final Bot bot;
    final Set<String> joins = ConcurrentHashMap.newKeySet();
    final Map<String, List<String>> batches = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_BATCH_CACHE_SIZE;
        }
    });
    final Map<String, ScheduledFuture<?>> scheduledChecks;
    final String aiPrompt;
    final String[] susRoles;
    final Map<String, Long> susRoleOverride = new HashMap<>();
    final int minMembersToScan;
    final long stopBeingSus;
    final Map<String, Integer> kickRejoinTimes = new HashMap<>();

    OffsetDateTime lastJoin = OffsetDateTime.MIN;
    OffsetDateTime lastCheck = OffsetDateTime.now();
    int lastCheckAmount = -1;
    long lastCheckMinutes = -1;

    public Gatekeeper(Bot bot, JSONObject json) {
        String tempAiPrompt;
        this.bot = bot;
        Gatekeeper.Scheduler.scheduleWithFixedDelay(this::runChecks, 30, 20, TimeUnit.MINUTES);

        if (bot.isFeatureEnabled(Feature.Gatekeeper_RequireOnboarding)) {
            scheduledChecks = new ConcurrentHashMap<>();
        } else {
            scheduledChecks = null;
        }

        try {
            tempAiPrompt = Files.readString(Path.of("data/gatekeeper-override.txt"));
            Log.warn("Using custom Gatekeeper AI. Improper configuration will cause issues!");
        } catch (Exception e) {
            tempAiPrompt = bot.readResourceString("genai/gatekeeper.txt");
            if (tempAiPrompt == null) {
                Log.error("Cannot read internal resource... disabling Gatekeeper");
                bot.disableFeature(Feature.Gatekeeper);
            }
        }

        this.aiPrompt = tempAiPrompt;
        if (!bot.isFeatureEnabled(Feature.AI)) {
            Log.error("Requires feature flag AI, disabling Gatekeeper");
            bot.disableFeature(Feature.Gatekeeper);
        }

        if (json.optJSONArray("susRoles") != null) {
            susRoles = new String[json.optJSONArray("susRoles").length()];
            for (int i = 0; i < json.optJSONArray("susRoles").length(); i++) {
                Object obj = json.getJSONArray("susRoles").get(i);
                if (obj instanceof String s) {
                    susRoles[i] = s;
                } else if (obj instanceof JSONObject jsonObject) {
                    susRoles[i] = jsonObject.getString("id");
                    susRoleOverride.put(susRoles[i], jsonObject.optLong("override", 0));
                }
            }
        } else {
            Log.error("Cannot find susRoles array in config... disabling Gatekeeper");
            bot.disableFeature(Feature.Gatekeeper);
            susRoles = null;
        }
        this.minMembersToScan = json.optInt("minMembersToScan", 16);
        this.stopBeingSus = json.optLong("stopBeingSus", 30);
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        joins.add(event.getUser().getId());
        lastJoin = OffsetDateTime.now();
        if (bot.isFeatureEnabled(Feature.Gatekeeper_RequireOnboarding)) {
            int timer = (int) (Math.random() * 50 + 15);
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

        if (!this.precheck() || !bot.isFeatureEnabled(Feature.Gatekeeper)) return;
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
            if (!list.isEmpty()) {
                String batch = String.valueOf(System.currentTimeMillis());
                batches.put(batch, list);
                bot.getIO().send(SetChannel.DeploymentBotCommands, bot.getMostModerators().getAsMention(), createContainer(batch, list, this.lastCheck.toEpochSecond()), this);
            }
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
        if (this.lastJoin.isAfter(OffsetDateTime.now().minusMinutes(15))) {
            Log.debug("Did not run checks b/c join in last 15 mins");
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

    private Container createContainer(String batch, List<String> memberIDs, long timeUntilScan) {
        String icon = bot.getDeploymentGuild().getIconUrl() == null ? "" : bot.getDeploymentGuild().getIconUrl();

        StringBuilder mentions = new StringBuilder();
        for (String memberID : memberIDs) {
            mentions.append("<@").append(memberID).append("> ");
        }

        return Container.of(
                TextDisplay.of("# :shield: Recent Join Activity"),
                Section.of(
                        Thumbnail.fromUrl(icon),
                        TextDisplay.of(String.format("From **<t:%s:t>** to **<t:%s:t>**, **%,d potential spambot(s)** joined.", timeUntilScan, OffsetDateTime.now().toEpochSecond(), memberIDs.size())),
                        TextDisplay.of(String.format("## Details:\n> %s", mentions))
                ),
                ActionRow.of(
                        Button.primary(identifier() + ";action;" + batch, "Remove..."),
                        Button.secondary(identifier() + ";kickall;" + batch, "Remove all"),
                        Button.secondary(identifier() + ";nothing;" + batch, "Do nothing")
                ),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# AI may make mistakes; use final judgement.").withUniqueId(FOOTER_NOTE)
        );
    }

    @Override
    public Container onButton(@NonNull ButtonInteractionEvent event, String... actions) {
        if (event.getMember() == null || actions.length < 3) return null;
        String action = actions[1];
        String batch = actions[2];
        long now = Instant.now().getEpochSecond();

        if (action.equals("nothing")) {
            batches.remove(batch);
            return lock(event, "-# Interaction locked <t:" + now + ":R> by `" + event.getMember().getEffectiveName() + "`.");
        }

        List<String> list = batches.get(batch);
        if (list == null) {
            return lock(event, "-# This review has expired.");
        }

        if (action.equals("kickall")) {
            batches.remove(batch);
            for (String memberID : list) {
                Member m = bot.getDeploymentGuild().getMemberById(memberID);
                if (m != null) m.kick().queue();
            }
            return lock(event, "-# Kick-all'd at <t:" + now + ":R> by `" + event.getMember().getEffectiveName() + "`.");
        }

        if (action.equals("action")) {
            StringSelectMenu.Builder selectMenu = StringSelectMenu.create("gatekeeper:target").setRequired(true).setRequiredRange(1, 8);
            for (String memberID : list) {
                Member member = bot.getDeploymentGuild().getMemberById(memberID);
                if (member != null) selectMenu.addOption(member.getUser().getName(), memberID, member.getEffectiveName());
            }
            if (selectMenu.getOptions().isEmpty()) {
                batches.remove(batch);
                return lock(event, "-# There were no more eligible targets for selection.");
            }
            Modal modal = Modal.create("modal:gatekeeper", "Remove Spambots")
                    .addComponents(
                            TextDisplay.of("Spambots are not notified when they're removed."),
                            Label.of("Eligible Targets", selectMenu.build()),
                            Label.of("Punishment", RadioGroup.create("gatekeeper:type")
                                    .addOption("Softban", "softban")
                                    .addOption("Ban", "ban")
                                    .build())
                    ).build();
            event.replyModal(modal).queue();
        }
        return null;
    }

    private Container lock(ButtonInteractionEvent event, String note) {
        return event.getMessage().getComponents().getFirst().asContainer()
                .replace(ComponentReplacer.byUniqueId(FOOTER_NOTE, TextDisplay.of(note)))
                .asDisabled();
    }

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {
        if (event.getModalId().equals("modal:gatekeeper")) {
            List<String> targets = event.getValues().getFirst().getAsStringList();
            boolean ban = event.getValues().get(1).getAsString().equals("ban");
            event.reply((ban ? "Banned" : "Softbanned") + " **" + targets.size() + "** members").setEphemeral(true).queue();
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
        if (m == null || m.hasPermission(Permission.ADMINISTRATOR)
         && m.getFlags().contains(Member.MemberFlag.COMPLETED_ONBOARDING)
         && m.getFlags().contains(Member.MemberFlag.BYPASSES_VERIFICATION)) {
            int amount = kickRejoinTimes.getOrDefault(id, 0);
            if (amount > 2) {
                bot.getIO().send(SetChannel.DebugAutoModAlert, String.format("It took %s %sx times to onboard the server.", id, time));
            }
            return;
        }
        m.kick().reason(String.format("Did not complete discord onboarding in %s", bot.formattedTime(time * 60L, false))).queue();
        this.kickRejoinTimes.putIfAbsent(id, 0);
        this.kickRejoinTimes.put(id, this.kickRejoinTimes.get(id) + 1);
    }

    public void removeLowQualityAccounts(String id) {
        if (!bot.isFeatureEnabled(Feature.Gatekeeper_RemoveLowQualityAccounts)) return;
        Member m = bot.getDeploymentGuild().getMemberById(id);
        if (m == null || m.hasPermission(Permission.ADMINISTRATOR)) return;
        Set<Role> susRoles = new HashSet<>();
        String roleToBecomeSus = "";
        for (String susRole : this.susRoles) {
            if (bot.getDeploymentGuild().getRoleById(susRole) != null) {
                susRoles.add(bot.getDeploymentGuild().getRoleById(susRole));
                roleToBecomeSus = susRole;
            }
        }
        if (susRoles.isEmpty() || Collections.disjoint(susRoles, m.getRoles())) return;
        long timeDifference = Math.abs(ChronoUnit.SECONDS.between(m.getUser().getTimeCreated(), OffsetDateTime.now()));
        long sus = susRoleOverride.getOrDefault(roleToBecomeSus, this.stopBeingSus);
        if (timeDifference < sus * 24 * 60 * 60) {
            m.kick().reason("Joined too quickly after account creation -- " + bot.formattedTime(timeDifference, false)).queue();
            this.kickRejoinTimes.putIfAbsent(id, 0);
            this.kickRejoinTimes.put(id, this.kickRejoinTimes.get(id) + 1);
        }
    }
}
