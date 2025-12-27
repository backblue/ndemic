package org.backblue.wrappers;

import com.azure.ai.contentsafety.models.AnalyzeImageOptions;
import com.azure.ai.contentsafety.models.AnalyzeImageResult;
import com.azure.ai.contentsafety.models.ContentSafetyImageData;
import com.azure.ai.contentsafety.models.ImageCategoriesAnalysis;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.Bot;
import org.backblue.utilities.ComponentManager;
import org.backblue.utilities.NdemicModule;
import org.backblue.utilities.SQLProfile;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

public class ProfileHandler implements NdemicModule {

    private static ArrayList<String> DETECTION_TEXT;
    private boolean forcedOff = false;

    public enum Source {
        GUILD_JOIN,
        PROFILE_UPDATE,
        SLASH,
        MESSAGE
    }

    public ProfileHandler(){}
    public ProfileHandler(Member member, ProfileHandler.Source source) {
        process(member, source);
    }

    @Override
    public String name() {
        return "profileScanning";
    }

    private void process(Member member, ProfileHandler.Source source) {
        if (isEnabled() && !this.forcedOff) {
            ProfileAnalysisRecord record = ProfileAnalysisRecord.of(member);

            SQLProfile profile = SQLProfile.read(record.user.getId(), "userinfo");
            if (record.customStatus != null) {
                if (basicTextCheck(record.customStatus)) {
                    Bot.getBot().additionalReview(member, true, ComponentManager.ComponentPreset.CUSTOM_STATUS, record.customStatus);
                    profile.writeString("lastCustomStatus", record.customStatus)
                            .writeString("lastProfileScan", String.valueOf(Instant.now().getEpochSecond()))
                            .write();
                    return;
                }
            }

            ScanResult avatarResults = processImage(record, record.avatarURL, "Avatar");
            ScanResult bannerResults = processImage(record, record.bannerURL, "Banner");

            if (avatarResults != null && avatarResults.points >= Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("pointsThresholdToWarn")) {
                Bot.getBot().additionalReview(member, false, ComponentManager.ComponentPreset.PROFILE_PICTURE, record.avatarURL, String.valueOf(avatarResults.results));
                return;
            }
            if (bannerResults != null && bannerResults.points >= Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("pointsThresholdToWarn")) {
                Bot.getBot().additionalReview(member, false, ComponentManager.ComponentPreset.BANNER, record.bannerURL, String.valueOf(bannerResults.results));
                return;
            }

            int currentPoints;
            int aR = 0;
            int bR = 0;
            final int pointsToWarn = Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("pointsThresholdToWarn");
            if (profile.readInt("profilePoints") == null) {
                currentPoints = 0;
            } else if (profile.readInt("lastRefresh") + Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("resetPointsAfterSeconds") > Instant.now().getEpochSecond()) {
                currentPoints = 0;
            } else {
                currentPoints = profile.readInt("profilePoints");
            }
            if (avatarResults != null) {
                aR = avatarResults.points;
            }
            if (bannerResults != null) {
                bR = bannerResults.points;
            }
            if (avatarResults != null && avatarResults.points + currentPoints >= pointsToWarn) {
                Bot.getBot().additionalReview(member, false, ComponentManager.ComponentPreset.OTHER, "Past Trend/Potential Inappropriate Avatars", record.avatarURL);
                currentPoints = 0;
                aR = 0;
            }
            if (bannerResults != null && bannerResults.points + currentPoints >= pointsToWarn) {
                Bot.getBot().additionalReview(member, false, ComponentManager.ComponentPreset.OTHER, "Past Trend/Potential Inappropriate Banners", record.bannerURL);
                currentPoints = 0;
                bR = 0;
            }

            if (avatarResults != null) {
                currentPoints += aR;
            }
            if (bannerResults != null) {
                currentPoints += bR;
            }

            profile.writeString("lastCustomStatus", record.customStatus)
                    .writeInt("profilePoints", currentPoints)
                    .write();

        }
    }

    private ScanResult processImage(ProfileAnalysisRecord record, String url, String type) {
        if (url == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = downloadUrl(new URL(url));
            FileUpload imageFile = FileUpload.fromData(bytes, record.user.getName() + "_" + type.toLowerCase() + ".png");
            Bot.getBot().sendDebugMessage("imageDump", "`" + record.user.getId() + "`", imageFile);
            if (type.equals("Avatar")) {
                record.avatarFile = imageFile;
            }
            if (type.equals("Banner")) {
                record.bannerFile = imageFile;
            }
        } catch (MalformedURLException e) {
            return null;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
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
                bytes = baos.toByteArray();
            }
        } catch (IOException ignored) {
        }

        ContentSafetyImageData image = new ContentSafetyImageData();
        image.setContent(BinaryData.fromBytes(bytes));
        AnalyzeImageResult response;

        try {
            response = Bot.getBot().getContentSafetyClient().analyzeImage(new AnalyzeImageOptions(image));
        } catch (HttpResponseException e) {
            if (e.getResponse().getStatusCode() == 403) {
                this.forcedOff = true;
                Bot.getBot().sendDeploymentMessage("autoMod", "Profile scanning cannot continue due to 403 error: `" + e.getMessage()+" `");
            } else {
                Bot.getBot().sendDebugMessage("autoMod", "Failed to analyze image for " + record.user.getName() + " (" + record.user.getId() + ") due to: " + e.getMessage());
            }
            return null;
        }
        HashMap<String, Integer> results = new HashMap<>();
        Integer points = 0;
        for (ImageCategoriesAnalysis analysis : response.getCategoriesAnalysis()) {
            results.put(analysis.getCategory().toString(), analysis.getSeverity());
            points += analysis.getSeverity();
            if (analysis.getCategory().toString().equals("Hate") && analysis.getSeverity() > 0) {
                points = Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("pointsThresholdToWarn") * 2;
                break;
            }
        }
        SQLProfile.read(record.user.getId(), "userinfo").writeString("lastProfileScan", String.valueOf(Instant.now().getEpochSecond()))
                .writeString("last" + type + "URL", url)
                .write();
        return new ScanResult(points/2, results);
    }

    private byte[] downloadUrl(URL toDownload) throws MalformedURLException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[4096];
            int bytesRead;
            InputStream stream = toDownload.openStream();

            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }
            outputStream.close();
            stream.close();

        } catch (Exception e) {
            throw new MalformedURLException("There's an error converting the URL contents to an array.\nLink provided: '" + toDownload + "'\n" + e);
        }
        return outputStream.toByteArray();
    }

    private boolean basicTextCheck(@NotNull String text) {
        if (ProfileHandler.DETECTION_TEXT == null) {
            int size = Integer.parseInt(Bot.getBot().getDeployment().get("basicWordChkList.size"));
            ProfileHandler.DETECTION_TEXT = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ProfileHandler.DETECTION_TEXT.add(Bot.getBot().getDeployment().get("basicWordChkList." + i));
            }
        }
        for (String detection : ProfileHandler.DETECTION_TEXT) {
            if (text.toLowerCase().contains(detection.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static class ProfileAnalysisRecord {
        private User user;
        private String avatarURL;
        private String bannerURL;
        private String customStatus;
        private FileUpload avatarFile;
        private FileUpload bannerFile;

        private static ProfileAnalysisRecord of(Member member) {
            ProfileAnalysisRecord record = new ProfileAnalysisRecord();
            record.user = member.getUser();
            record.avatarURL = record.user.getAvatarUrl();
            record.user.retrieveProfile().queue(profile -> record.bannerURL = profile.getBannerUrl());

            for (Activity activity : member.getActivities()) {
                if (activity.getType() == Activity.ActivityType.CUSTOM_STATUS) {
                    record.customStatus = activity.getName();
                }
            }

            SQLProfile userProfile = SQLProfile.read(record.user.getId(), "userinfo");
            if (record.avatarURL != null && record.avatarURL.equals(userProfile.readString("lastAvatarURL"))) {
                record.avatarURL = null;
            }
            if (record.bannerURL != null && record.bannerURL.equals(userProfile.readString("lastBannerURL"))) {
                record.bannerURL = null;
            }
            if (record.customStatus != null && record.customStatus.equals(userProfile.readString("lastCustomStatus"))) {
                record.customStatus = null;
            }

            return record;
        }
    }
    private record ScanResult(int points, HashMap<String, Integer> results) {}

}
