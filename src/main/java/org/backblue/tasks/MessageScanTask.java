package org.backblue.tasks;

import com.azure.ai.contentsafety.models.*;
import com.azure.core.exception.HttpResponseException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

public final class MessageScanTask extends Task {
    private static final HashMap<User, Long> timeBetweenScans = new HashMap<>();

    private @NotNull final User user;
    private @NotNull final Message msg;

    public MessageScanTask(String user, @NotNull Message msg) {
        super();
        this.user = Objects.requireNonNull(Bot.getBot().getJDA().getUserById(user));
        this.msg = msg;
        Bot.getBot().getTaskQueue().add(this);
    }

    @Override
    public void process() {
        this.markStarted();
        HashMap<String, Integer> result = processMessage(msg.getContentRaw());
        if (result == null) {
            this.markDoneWithWarning("Azure rejected text analysis request");
            return;
        }
        for (String category : result.keySet()) {
            if (result.get(category) >= Bot.getBot().getTasks().getJSONObject("messageScanning").getJSONObject("detection").getInt(category)) {
                boolean silent = false;
                boolean blocked = false;
                if (Bot.getBot().getTasks().getJSONObject("messageScanning").getBoolean("autoDelete")) {
                    this.msg.delete().queue();
                    blocked = true;
                }
                if (Bot.getBot().getTasks().getJSONObject("messageScanning").getBoolean("alert")) {
                    silent = true;
                }
                if (!timeBetweenScans.containsKey(this.user) || timeBetweenScans.get(user) + 30 > Instant.now().getEpochSecond()) {
                    warnModerators(silent, blocked, this.msg);
                    timeBetweenScans.put(this.user, Instant.now().getEpochSecond());
                }
                break;
            }
        }
        this.markDone();
    }

    private void warnModerators(boolean silent, boolean blocked, Message msg) {
        String flagged = blocked ? "Blocked" : "Flagged";
        String moderatorPing = "";
        String gotoMessage = "";
        if (!silent) {
            moderatorPing = Bot.getBot().getMostModerators().getAsMention();
        }
        if (flagged.equals("Flagged")) {
            gotoMessage = "\n**Click to jump to message:**: " + msg.getJumpUrl();
        }
        Bot.getBot().sendDeploymentMessage("cmd", moderatorPing + " Message " + flagged + " of " + user.getAsMention() + "\n**Message:**\n> " + this.msg.getContentRaw() + "\n**Details**:\n```" + this.output + "```" + gotoMessage);
    }

    private HashMap<String, Integer> processMessage(String content) {
        AnalyzeTextResult response;
        try {
            response = Bot.getBot().getContentSafetyClient().analyzeText(new AnalyzeTextOptions(content));
        } catch (HttpResponseException e) {
            Bot.getBot().sendDebugMessage("autoMod", "Failed to analyze msg text for " + user.getName() + " (" + user.getId() + ") due to: " + e.getMessage());
            return null;
        }
        HashMap<String, Integer> results = new HashMap<>();
        for (TextCategoriesAnalysis analysis : response.getCategoriesAnalysis()) {
            results.put(analysis.getCategory().toString(), analysis.getSeverity());
        }
        this.appendOutput("Results: " + results);
        return results;
    }

    @Override
    public HashMap<String, String> lookup() {
        HashMap<String, String> map = lookupBase();
        map.put("msg", msg.getContentRaw());
        map.put("userID", user.getId());
        map.put("user", user.getName());
        return map;
    }
}
