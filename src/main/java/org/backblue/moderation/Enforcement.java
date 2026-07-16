package org.backblue.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Enforcement extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(Enforcement.class);

    final Bot bot;
    final Pattern[] regex;
    final double probability;

    public Enforcement(Bot bot, JSONObject config) {
        this.bot = bot;
        if (config.getJSONObject("regex") != null) {
            this.regex = new Pattern[config.getJSONArray("regex").length()];
            for (int i = 0; i < config.getJSONArray("regex").length(); i++) {
                this.regex[i] = Pattern.compile(config.getJSONArray("regex").getString(i));
            }
        } else {
            this.regex = new Pattern[0];
        }
        this.probability = config.optDouble("rollingProbability", 0.02);
        try {
            int r = (int) (Math.random() * 30 + 60);
            bot.getScheduler().scheduleWithFixedDelay(this::check, (long) (r*1.25), r, TimeUnit.MINUTES);
        } catch (Exception e) {
            Log.error("Unhandled Error: {}", e.getMessage());
        }
    }

    private void check() {
        if (!bot.isFeatureEnabled(FeatureFlag.Gatekeeper_Enforcement)) return;
        if (probability <= 0 && this.regex.length == 0) {
            Log.error("Badly configured Enforcement, disabling");
            bot.disableFeature(FeatureFlag.Gatekeeper_Enforcement);
            return;
        }

        List<Member> members = bot.getDeploymentGuild().getMembers();
        int amountToTake = Math.clamp((int) (members.size() * probability), 250, 700);
        for (int i = 0; i <= amountToTake; i++) {
            this.check(members.get(i));
        }
    }

    private void check(Member member) {
        if (member.hasPermission(Permission.ADMINISTRATOR)) return;
        member.getGuild().searchMessages()
                .authors(member)
                .limit(10)
                .queue(response -> {
                    if (response.isNotReady()) return;
                    List<Message> messages = response.asResults().getMessages();
                    if (messages.size() >= 3) return;

                    for (Pattern pattern : regex) {
                        Matcher matcher = pattern.matcher(member.getUser().getEffectiveName());
                        if (matcher.find()) {
                            Log.info("Enforcement: Found username match for user {}", member.getId());
                        }
                    }
                });
    }
}
