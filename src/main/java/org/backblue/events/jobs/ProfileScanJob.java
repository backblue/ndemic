package org.backblue.events.jobs;

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
import org.backblue.utilities.SQLJSON;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ProfileScanJob extends Job {

    private final User user;
    private String avatarLink;
    private String bannerLink;
    private final String source;

    public ProfileScanJob(@NotNull ProfileScanJob job) {
        super();
        this.user = Core.BOT.getUserById(id);

        this.source = job.source;
        if (user != null) {
            this.avatarLink = user.getAvatarUrl();
            user.retrieveProfile().queue(profile -> this.bannerLink = profile.getBannerUrl());
            QUEUE.add(this);
        }
    }

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

            user.retrieveProfile().queue(profile -> this.bannerLink = profile.getBannerUrl());
        try {
            if (json.readString("lastBannerURL").equals(bannerLink)) {
                bannerLink = null;
            }
        } catch (Exception ignored) {}

        if (this.avatarLink == null) {
            return;
        }

        QUEUE.add(this);
    }

    @Override
    public void process() {
        this.started = System.currentTimeMillis();
        if (!Core.SAFETY.getJSONObject("scanProfile").getBoolean("enabled")) {
            return;
        }
        QUEUE.remove();
        if (user == null || user.isBot()) {
            markInvalid("User is null or bot");
            return;
        }

        if (nextJobSamePerson() != null) {
            log();
            markInvalid("User's profile no longer up-to-date, retrying");
            new ProfileScanJob(this);
            return;
        }

        TextChannel guildLog = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.log"));
        TextChannel guildCmd = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));
        Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("alerts.optIn"));

        List<ImageCategoriesAnalysis> avatarList = scanImage(avatarLink, "Avatar");
        List<ImageCategoriesAnalysis> bannerList = scanImage(bannerLink, "Banner");

        RECENT_COMPLETE_JOBS.push(this);

        String foundBadThing = "";

        if (avatarList != null) {
            for (ImageCategoriesAnalysis analysis : avatarList) {
                if (analysis.getSeverity() >= Core.SAFETY.getJSONObject("trigger").getInt(analysis.getCategory().toString())) {
                    foundBadThing = "Avatar";
                    String fail = ":warning: `#" + this.id + "`: Scanned " + user.getAsMention() + "'s [avatar](<" + avatarLink + ">].\n-#From: `" + source + "`";
                    if (guildLog != null) {
                        guildLog.sendMessage(fail).queue();
                    }
                    break;
                }
            }
        }
        if (foundBadThing.isEmpty() && bannerList != null) {
            for (ImageCategoriesAnalysis analysis : bannerList) {
                if (analysis.getSeverity() >= Core.SAFETY.getJSONObject("trigger").getInt(analysis.getCategory().toString())) {
                    foundBadThing = "Banner";
                    String fail = ":warning: `#" + this.id + "`: Scanned " + user.getAsMention() + "'s [banner](<" + bannerLink + ">].\n-#From: `" + source + "`";
                    if (guildLog != null) {
                        guildLog.sendMessage(fail).queue();
                    }
                    break;
                }
            }
        }
        if (!foundBadThing.isEmpty()) {
            String msg = pingRole.getAsMention() + "Inappropriate " + foundBadThing.toLowerCase() + "found: " + user.getAsMention() + "'s " + foundBadThing.toLowerCase() + "\n" + "Link: " + (foundBadThing.equals("Avatar") ? avatarLink : bannerLink) + "\n" + "-# From: `" + source + "`";
            if (guildCmd != null) {
                guildCmd.sendMessage(msg).queue();
            }
            markDoneWithPrejudice("Potential Inappropriate " + foundBadThing.toLowerCase() + " detected");
        } else {
            String good = ":white_check_mark: `#" + this.id + "`: Scanned " + user.getAsMention() + "'s profile.\n-# From: `" + source + "`";
            if (guildLog != null) {
                guildLog.sendMessage(good).queue();
            }
            markDone();
        }

        log();
    }

    @Override
    public HashMap<String, String> lookup() {
        HashMap<String, String> info = super.lookup();
        info.put("user", user.getName());
        info.put("avatar", avatarLink);
        info.put("banner", bannerLink);
        info.put("source", source);
        return info;
    }

    @Override
    public String toString() {
        return "`" + this.id + "`: **" + user.getId() + "** " + this.getClass().getSimpleName() + " " + getOutput();
    }

    private List<ImageCategoriesAnalysis> scanImage(String link, String type) {

        if (type.equals("Banner") && link == null) {
            return null;
        }

        byte[] imageData;
        try {
            imageData = downloadUrl(new URL(link));
        } catch (MalformedURLException e) {
            ProfileScanJob newJob = new ProfileScanJob(this);
            this.markInvalid(type + " link leads to nowhere, Retrying on job `" + newJob.id + "`");
            return null;
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
            BufferedImage img = ImageIO.read(bais);
            int width = img.getWidth();
            int height = img.getHeight();

            if (width < 50 || height < 50) {
                width = Math.max(50, width);
                height = Math.max(50, height);
                BufferedImage resized = new BufferedImage(width, height, img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType());
                Graphics2D g2d = resized.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(img, 0, 0, width, height, null);
                g2d.dispose();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resized, "png", baos);
                imageData = baos.toByteArray();
                appendOutput(" " + type + ": Resized to `" + width + " x " + height + "` ");
            }

        } catch (IOException e) {
            ProfileScanJob newJob = new ProfileScanJob(this);
            this.markInvalid("Could not convert " + type + " image to byte array. Retrying on job `" + newJob.id + "`");
            return null;
        }

        ContentSafetyImageData image = new ContentSafetyImageData();
        image.setContent(BinaryData.fromBytes(imageData));
        AnalyzeImageResult response;

        try {
            response = Core.CONTENT_SAFETY_CLIENT.analyzeImage(new AnalyzeImageOptions(image));
        } catch (HttpResponseException e) {
            TextChannel emergency = Core.BOT.getTextChannelById(Core.ANALYTICS.get("autoMod"));
            Objects.requireNonNull(emergency).sendMessage("Manual " + type + " review needed! Skipping... " + link + "\n`" + e + "`").queue();
            return null;
        }

        HashMap<String, Integer> results = new HashMap<>();
        for (ImageCategoriesAnalysis analysis : response.getCategoriesAnalysis()) {
            results.put(analysis.getCategory().toString(), analysis.getSeverity());
        }
        appendOutput(results.toString());
        SQLJSON.read(user.getId(), "userinfo").writeString("lastProfileScan", String.valueOf(Instant.now().getEpochSecond()))
                .writeString("last" + type + "URL", link)
                .write("userinfo");

        return response.getCategoriesAnalysis();
    }

    private @Nullable Job nextJobSamePerson() {
        String thisJobUserID = this.user.getId();
        for (Job job : QUEUE) {
            if (job instanceof ProfileScanJob && ((ProfileScanJob) job).user.getId().equals(thisJobUserID)) {
                return job;
            }
        }
        return null;
    }
}
