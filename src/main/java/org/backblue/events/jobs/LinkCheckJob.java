package org.backblue.events.jobs;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.Core;
import org.backblue.InvalidBotStateException;
import org.backblue.utilities.TakeAction;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkCheckJob extends Job {

    private final MessageReceivedEvent message;
    private String messageRaw;
    private final List<String> links;
    private JSONObject jsonResponse = new JSONObject();

    private static final HashSet<String> badLinks = new HashSet<>();

    public LinkCheckJob(MessageReceivedEvent message, List<String> links) {
        super();
        this.message = message;
        this.links = links;
        this.messageRaw = message.getMessage().getContentRaw();
        QUEUE.add(this);
    }

    @Override
    public void process() {
        this.started = System.currentTimeMillis();

        QUEUE.remove();

        boolean response = false;
        for (String link : this.links) {
            try {
                response = sendRequest(link);
                if (response) {
                    TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));
                    Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("alerts.optIn"));
                    channel.sendMessage(pingRole.getAsMention() + "\n" + Core.BOT.getSelfUser().getAsMention() + " thinks there is a malicious link from " + message.getMember().getAsMention() + "\n\nMessage:\n> " + message.getMessage().getContentRaw()).queue();
                    if (!response) {
                        response = true;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (response) {
            markDoneWithPrejudice("Malicious Links");
            if (Core.SAFETY.getJSONObject("linkChecks").getBoolean("takeAction")) {
                TakeAction.kickWarnLog(message.getMember(), "Malicious Links\n### Full Message:\n> `" + messageRaw + "`");
            }
        } else {
            markDone();
        }

        log();
        RECENT_COMPLETE_JOBS.push(this);
    }

    @Override
    public HashMap<String, String> lookup() {
        HashMap<String, String> map = super.lookup();
        map.put("message", message.getMessage().getContentRaw());
        map.put("user", message.getAuthor().getAsTag());
        map.put("response", jsonResponse.toString());
        map.put("links", links.toString());
        map.put("output", jsonResponse + map.get("output"));
        return map;
    }

    @Override
    public String toString() {
        return "`" + this.id + "`: **" + message.getMember().getId() + "** " + this.getClass().getSimpleName() + " " + getOutput();
    }

    public static @NotNull List<String> getLinks(String str) {
        List<String> links = new ArrayList<>();
        String regex = "(https?://\\S+)|(www\\.\\S+)";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            links.add(matcher.group());
        }
        return links;
    }

    private @NotNull JSONObject generatePayload(String url) {

        JSONObject payload = new JSONObject();
        payload.put("client", new JSONObject());
        payload.getJSONObject("client").put("clientId", Core.SETTINGS.get("identifier"));
        payload.getJSONObject("client").put("clientVersion", Core.VERSION);

        payload.put("threatInfo", new JSONObject());

        String[] types = {"MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"};
        String[] platforms = {"ANY_PLATFORM"};
        String[] threats = {"URL"};
        JSONObject theUrl = new JSONObject().put("url", url);

        payload.getJSONObject("threatInfo").put("threatTypes", types);
        payload.getJSONObject("threatInfo").put("platformTypes", platforms);
        payload.getJSONObject("threatInfo").put("threatEntryTypes", threats);
        payload.getJSONObject("threatInfo").put("threatEntries", new JSONObject[]{theUrl});

        return payload;
    }

    private boolean sendRequest(String url) throws Exception {
        boolean returnValue = false;
        url = url.trim();
        url = url.toLowerCase();
        while (url.endsWith(".") || url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        if (badLinks.contains(url)) {
            this.appendOutput("Previous Link that was flagged");
            returnValue = true;
        }

        if (!returnValue) {
            JSONObject payload = generatePayload(url);

            URL endpointUrl = new URL(Core.SECURE_KEYS.getProperty("GOOGLE_SAFE_BROWSING_KEY_ENDPOINT"));
            String jsonPayload = payload.toString();

            HttpURLConnection conn = (HttpURLConnection) endpointUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            Scanner scanner = new Scanner(conn.getInputStream());
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONObject jsonResponse = new JSONObject(response);
            if (!jsonResponse.isEmpty()) {
                returnValue = true;
                badLinks.add(url);
                this.jsonResponse = jsonResponse.getJSONArray("matches").getJSONObject(0);
                this.appendOutput("Link flagged as unsafe: " + url);
            }
        }

        TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.log"));
        String pass = ":white_check_mark: `#" + id + "`: Scanned link `" + url + "` sent by " + message.getAuthor().getAsMention() + ".";
        String failed = ":white_check_mark: `#" + id + "`: Scanned link `" + url + "` sent by " + message.getAuthor().getAsMention() + " is found to be potentially **malicious**.";
        if (jsonResponse.isEmpty()) {
            channel.sendMessage(pass).queue();
        } else {
            channel.sendMessage(failed).queue();
            this.message.getMessage().delete().queue();
        }

        return returnValue;
    }

}
