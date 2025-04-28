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

public class ProfileScanJob extends Job {

    private final User user;
    private String avatarLink;
    private String bannerLink;
    private final String source;

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

        if (this.avatarLink == null) {
            return;
        }

        QUEUE.add(this);
    }

    @Override
    public void process() {
        this.started = System.currentTimeMillis();
        TextChannel failurePing = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));

        if (!Core.SAFETY.getJSONObject("scanProfile").getBoolean("enabled")) {
            return;
        }
        QUEUE.remove();
        if (user == null || user.isBot()) {
            markInvalid("User is null or bot");
            return;
        }

        Boolean avatarAlert = scanImage(avatarLink, "Avatar", id);
        Boolean bannerAlert = scanImage(bannerLink, "Banner", id);
        RECENT_COMPLETE_JOBS.push(this);

        if (avatarAlert == null) {
            markInvalid("Avatar scan failed");
            log();
            failurePing.sendMessage("<@387336581775884288> <@852609613253443584> Job `" + id + "` failed");
            return;
        }
        if (bannerAlert == null) {
            markInvalid("Banner scan failed");
            log();
            failurePing.sendMessage("<@387336581775884288> <@852609613253443584> Job `" + id + "` failed");
            return;
        }

        TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));

        if (avatarAlert != null && avatarAlert) {
            markDoneWithPrejudice("Potential Inappropriate Avatar");
            Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("role.mod"));
            channel.sendMessage(pingRole.getAsMention() + "\n" + Core.BOT.getSelfUser().getAsMention() + " flags this avatar of user: " + user.getAsMention() + "\n" + avatarLink + "\n-# Output: " + getOutput() + ", Triggered from: " +source).queue();
        } else if (bannerAlert != null && bannerAlert) {
            Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("role.mod"));
            channel.sendMessage(pingRole.getAsMention() + "\n" + Core.BOT.getSelfUser().getAsMention() + " flags this banner of user: " + user.getAsMention() + "\n" + bannerLink + "\n-# Output: " + getOutput() + ", Triggered from: " +source).queue();
            markDoneWithPrejudice("Potential Inappropriate Banner");
        } else {markDone();}
        log();
    }

    @Override
    public HashMap<String, String> lookup() {
        HashMap<String, String> info = super.lookup();
        info.put("user", user.getName());
        info.put("avatar", avatarLink);
        info.put("banner", bannerLink);
        info.put("output", getOutput());
        info.put("type", this.getClass().getSimpleName());
        info.put("source", source);
        return info;
    }

    @Override
    public String toString() {
        return "`" + this.id + "`: **" + user.getId() + "** " + this.getClass().getSimpleName() + " " + getOutput();
    }

    private Boolean scanImage(String link, String type, int jobNo) {

        if (link == null) {
            return false;
        }

        ContentSafetyImageData image = new ContentSafetyImageData();
        byte[] imageData;

        try {
            imageData = downloadUrl(new URL(link));
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL Data! URL Attempted: " + link + "\nJob ID:" + jobNo + "\n" + e);
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
                appendOutput(" Resized image to `" + width + " x " + height + "` ");
            }

        } catch (IOException e) {
            System.out.println("Can't convert to ImageIO" + link);
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
        if (channel != null) {
            String fail = ":warning: `#" + jobNo + "`: Scanned [" + type + "](<" + link + ">) of " + user.getAsMention() + " **with problems**.\n-# Triggered by: `" + source + "`";
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
                channel.sendMessage(":white_check_mark: `#" + jobNo + "`: Scanned [" + type + "](<" + link + ">) of " + user.getAsMention() + "." + "\n-# Triggered by: `" + source + "`").queue();
                return false;
            }
        }
        System.out.println("Channel not found - ID: " + Core.DEPLOYMENT.get("channel.log"));
        return null;
    }
}
