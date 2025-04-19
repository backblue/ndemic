package org.backblue.libraries;

import com.azure.ai.contentsafety.models.AnalyzeImageOptions;
import com.azure.ai.contentsafety.models.AnalyzeImageResult;
import com.azure.ai.contentsafety.models.ContentSafetyImageData;
import com.azure.ai.contentsafety.models.ImageCategoriesAnalysis;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.backblue.Core;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;

public class ProfileScanJob extends Job {

    private final User user;
    private String avatarLink;
    private String bannerLink;
    private String source;

    public ProfileScanJob(String id, String source) {
        super();
        this.user = Core.BOT.getUserById(id);
        this.source = source;

        SQLJSON json = SQLJSON.read(id, "userinfo");

        if (this.user == null || this.user.isBot()) {
            super.ignore();
            return;
        }

        try {
            if (!json.readString("lastAvatarURL").equals(user.getAvatarUrl())) {
                this.avatarLink = user.getAvatarUrl();
            }
        } catch (NullPointerException e) {
            this.avatarLink = user.getAvatarUrl();
        }

            user.retrieveProfile().queue(profile -> {
                this.bannerLink = profile.getBannerUrl();
            });
        try {
            if (json.readString("lastBannerURL").equals(bannerLink)) {
                bannerLink = null;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void process() {
        if (!Core.SAFETY.getJSONObject("scanProfile").getBoolean("enabled")) {
            return;
        }
        Job job = QUEUE.remove();
        Boolean avatarAlert = ((ProfileScanJob) job).scanImage(avatarLink, "Avatar");
        Boolean bannerAlert = ((ProfileScanJob) job).scanImage(bannerLink, "Banner");

        if (avatarAlert != null && avatarAlert) {
            TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));
            Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("role.mod"));
            channel.sendMessage(pingRole.getAsMention() + "\n" + Core.BOT.getSelfUser().getAsMention() + " flags this avatar of user: " + user.getAsMention() + "\n" + avatarLink + "\nOutput: " + getOutput() + ", Triggered from: " +source).queue();
        } else if (bannerAlert != null && bannerAlert) {
            TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));
            Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("role.mod"));
            channel.sendMessage(pingRole.getAsMention() + "\n" + Core.BOT.getSelfUser().getAsMention() + " flags this banner of user: " + user.getAsMention() + "\n" + bannerLink + "\nOutput: " + getOutput() + ", Triggered from: " +source).queue();
        }

        RECENT_COMPLETE_JOBS.add(job);
        job.markDone();
    }

    @Override
    public HashMap<String, String> lookup() {
        HashMap<String, String> info = new HashMap<>();
        info.put("user", user.getName());
        info.put("avatar", avatarLink);
        info.put("banner", bannerLink);
        info.put("output", getOutput());
        info.put("type", String.valueOf(this.getClass()));
        info.put("done", String.valueOf(getCompleted()));
        info.put("source", source);
        return info;
    }

    @Override
    public String toString() {
        return "ID: " + this.id + " " + user.getId() + " " + this.getClass() + " " + getOutput();
    }

    private Boolean scanImage(String link, String type) {

        if (link == null) {
            return false;
        }

        ContentSafetyImageData image = new ContentSafetyImageData();
        byte[] imageData;

        try {
            imageData = downloadUrl(new URL(link));
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL Data! URL Attempted: " + link);
            return null;
        }

        image.setContent(BinaryData.fromBytes(imageData));

        AnalyzeImageResult response;

        try {
            response = Core.CONTENT_SAFETY_CLIENT.analyzeImage(new AnalyzeImageOptions(image));
        } catch (HttpResponseException e) {
            TextChannel emergency = Core.BOT.getTextChannelById(Core.ANALYTICS.get("autoMod"));
            assert emergency != null;
            emergency.sendMessage("Manual " + type + " review needed! Skipping... " + link + "\n" + e).queue();
            return null;
        }

        HashMap<String, Integer> categories = new HashMap<>();
        for (ImageCategoriesAnalysis result : response.getCategoriesAnalysis()) {
            categories.put(result.getCategory().toString(), result.getSeverity());
        }

        appendOutput(categories.toString());

        SQLJSON.read(user.getId(), "userinfo").writeString("lastProfileScan", String.valueOf(Instant.now().getEpochSecond()))
                .writeString("last" + type + "URL", link)
                .write("userinfo");

        TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.log"));
        System.out.println("Successfully scanned profile of: " + user.getName() + " with results: " + getOutput());
        System.out.println(channel);
        if (channel != null) {
            String fail = ":x: **FAIL**: Processed " + type + " of: " + user.getAsMention();
            if (categories.get("SelfHarm") >= Core.SAFETY.getJSONObject("trigger").getInt("SelfHarm")) {
                channel.sendMessage(fail).queue();
                return true;
            } else if (categories.get("Sexual") >= Core.SAFETY.getJSONObject("trigger").getInt("Sexual")) {
                channel.sendMessage(fail).queue();
                return true;
            } else if (categories.get("Violence") >= Core.SAFETY.getJSONObject("trigger").getInt("Violence")) {
                channel.sendMessage(fail).queue();
                return true;
            } else if (categories.get("Hate") >= Core.SAFETY.getJSONObject("trigger").getInt("Hate")) {
                channel.sendMessage(fail).queue();
                return true;
            } else {
                channel.sendMessage(":white_check_mark: PASS: Processed\" + type + \" of: " + user.getAsMention()).queue();
                return false;
            }
        }
        return null;
    }
}
