package org.backblue.tasks;

import com.azure.ai.contentsafety.models.AnalyzeImageOptions;
import com.azure.ai.contentsafety.models.AnalyzeImageResult;
import com.azure.ai.contentsafety.models.ContentSafetyImageData;
import com.azure.ai.contentsafety.models.ImageCategoriesAnalysis;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.Bot;
import org.backblue.utilities.SQLProfile;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;

public final class ProfileScanTask extends Task {

    private static final HashMap<byte[], Integer> HASH_TO_POINTS;
    private static final ArrayList<byte[]> HASHES;
    private static final HashMap<byte[], HashMap<String, Integer>> HASH_TO_OUTPUT;
    private static ArrayList<String> DETECTION_TEXT;
    private @NotNull final User user;
    private String avatarURL;
    private String bannerURL;
    private String customStatus;
    private FileUpload avatarFile;
    private FileUpload bannerFile;
    private final String source; // Source is event that triggered scan, e.g. "join", "guildAvatarChange", "userAvatarChange", "slash"

    static {
        while (Bot.getBot().getJDA() == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }
        HASHES = new ArrayList<>();
        HASH_TO_POINTS = new HashMap<>();
        HASH_TO_OUTPUT = new HashMap<>();
        Path path = Paths.get("data/cache/imageScan.json");
        if (Bot.getBot().getSettings().getBoolean("caching") && path.toFile().exists()) {
            try {
                JSONObject packed = new JSONObject(Files.readString(path));
                for (String base64 : packed.keySet()) {
                    byte[] hash = Base64.getDecoder().decode(base64);
                    HASHES.add(hash);
                    HASH_TO_POINTS.put(hash, packed.getInt(base64));
                    HashMap<String, Integer> output = new HashMap<>();
                    output.put("cachePoints", packed.getInt(base64));
                    HASH_TO_OUTPUT.put(hash, output);
                }
            } catch (IOException e) {
                Bot.getBot().sendDebugMessage("autoMod", "Cannot read from file:\n`" + e.getMessage() + "`\nCache will not be used.");
            } catch (JSONException e) {
                Bot.getBot().sendDebugMessage("autoMod", "Disable cache or fix:\n`" + e.getMessage() + "`\nCache will not be used.");
            }
        }
    }

    public ProfileScanTask(String userID, String source) {
        super();
        this.user = Objects.requireNonNull(Bot.getBot().getJDA().getUserById(userID));
        this.avatarURL = user.getAvatarUrl();
        this.source = source;
        user.retrieveProfile().queue(profile -> this.bannerURL = profile.getBannerUrl());

        Member member = Objects.requireNonNull(Bot.getBot().getJDA().getGuildById(Bot.getBot().getDeployment().get("guild"))).getMemberById(user.getId());
        if (member != null) {
            for (Activity activity : member.getActivities()) {
                if (activity.getType() == Activity.ActivityType.CUSTOM_STATUS) {
                    customStatus = activity.getName();
                }
            }
        }
        SQLProfile userProfile = SQLProfile.read(user.getId(), "userinfo");
        if (avatarURL != null && avatarURL.equals(userProfile.readString("lastAvatarURL"))) {
            this.avatarURL = null;
        }
        if (bannerURL != null && bannerURL.equals(userProfile.readString("lastBannerURL"))) {
            this.bannerURL = null;
        }
        if (customStatus != null && customStatus.equals(userProfile.readString("lastCustomStatus"))) {
            this.customStatus = null;
        }
        completedTaskCreation();
    }

    public static JSONObject toBase64() {
        JSONObject packed = new JSONObject();
        for (byte[] hash : HASHES) {
            String base64 = Base64.getEncoder().encodeToString(hash);
            packed.put(base64, HASH_TO_POINTS.get(hash));
        }
        return packed;
    }

    public static int hashSize() {
        return HASHES.size();
    }

    @Override
    public void process() {
        this.markStarted();
        SQLProfile profile = SQLProfile.read(this.user.getId(), "userinfo");
        if (this.customStatus != null) {
            if (basicTextCheck(this.customStatus)) {
                warnModerators(this.user, "Custom Status", this.customStatus);
                markDoneWithWarning("Offensive Custom Status");
                profile.writeString("lastCustomStatus", this.customStatus)
                        .writeString("lastProfileScan", String.valueOf(Instant.now().getEpochSecond()))
                        .write("userinfo");
                return;
            }
        }
        Integer avatarResults = processImage(this.avatarURL, "Avatar");
        Integer bannerResults = processImage(this.bannerURL, "Banner");

        if (avatarResults != null && avatarResults >= 12) {
            warnModerators(this.user, "Avatar", output, avatarFile);
            markDoneWithWarning("Hateful Avatar");
            return;
        }
        if (bannerResults != null && bannerResults >= 12) {
            warnModerators(this.user, "Banner", output, bannerFile);
            markDoneWithWarning("Hateful Banner");
            return;
        }

        int currentPoints;
        final int pointsToWarn = Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("pointsThresholdToWarn");
        if (profile.readInt("profilePoints") == null) {
            currentPoints = 0;
        } else if (profile.readInt("lastRefresh") + Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("resetPointsAfterSeconds") > Instant.now().getEpochSecond()) {
            currentPoints = 0;
        } else {
            currentPoints = profile.readInt("profilePoints");
        }
        if (avatarResults != null && avatarResults + currentPoints >= pointsToWarn) {
            warnModerators(this.user, "Past Trend/Potential Inappropriate Avatars", this.customStatus, avatarFile);
            currentPoints = 0;
            avatarResults = 0;
            markDoneWithWarning("Trend of Inappropriate Avatars");
        }
        if (bannerResults != null && bannerResults + currentPoints >= pointsToWarn) {
            warnModerators(this.user, "Past Trend/Potential Inappropriate Banners", this.customStatus, bannerFile);
            currentPoints = 0;
            bannerResults = 0;
            markDoneWithWarning("Trend of Inappropriate Banners");
        }

        if (avatarResults != null) {
            currentPoints += avatarResults;
        }
        if (bannerResults != null) {
            currentPoints += bannerResults;
        }

        profile.writeString("lastCustomStatus", this.customStatus)
                .writeInt("profilePoints", currentPoints)
                .write("userinfo");

        this.markDone();

    }

    private Integer processImage(String url, String type) {
        if (url == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = downloadUrl(new URL(url));
            FileUpload imageFile = FileUpload.fromData(bytes, user.getName() + "_" + type.toLowerCase() + ".png");
            Bot.getBot().sendDebugMessage("imageDump", "`" + this + ": " + user.getId() + "`", imageFile);
            if (type.equals("Avatar")) {
                this.avatarFile = imageFile;
            }
            if (type.equals("Banner")) {
                this.bannerFile = imageFile;
            }
        } catch (MalformedURLException e) {

            return null;
        }
        if (ProfileScanTask.HASH_TO_POINTS.containsKey(bytes)) {
            return ProfileScanTask.HASH_TO_POINTS.get(bytes);
        }
        for (byte[] hash : HASHES) {
            System.out.println(getId() + ", " + type + ": " + compareByteArrays(hash, bytes));
            if (compareByteArrays(hash, bytes) < 100 - Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("similaritiesPercentageToNotScan")) {
                appendOutput(" " + type + ": Found similar image in cache, is: " + ProfileScanTask.HASH_TO_OUTPUT.get(hash));
                return ProfileScanTask.HASH_TO_POINTS.get(hash);
            }
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
                appendOutput(" " + type + ": Resized to **" + width + " x " + height + "** ");
            }
        } catch (IOException ignored) {}

        ContentSafetyImageData image = new ContentSafetyImageData();
        image.setContent(BinaryData.fromBytes(bytes));
        AnalyzeImageResult response;

        try {
            response = Bot.getBot().getContentSafetyClient().analyzeImage(new AnalyzeImageOptions(image));
        } catch (HttpResponseException e) {
            Bot.getBot().sendDebugMessage("autoMod", "Failed to analyze image for " + user.getName() + " (" + user.getId() + ") due to: " + e.getMessage());
            appendOutput("Azure rejected " + type.toLowerCase());
            return null;
        }
        HashMap<String, Integer> results = new HashMap<>();
        Integer points = 0;
        for (ImageCategoriesAnalysis analysis : response.getCategoriesAnalysis()) {
            results.put(analysis.getCategory().toString(), analysis.getSeverity());
            points += analysis.getSeverity();
            if (analysis.getCategory().toString().equals("Hate") && analysis.getSeverity() > 0) {
                points = Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("pointsThresholdToWarn")*2;
                break;
            }
        }
        appendOutput(type + ": " + results);
        SQLProfile.read(user.getId(), "userinfo").writeString("lastProfileScan", String.valueOf(Instant.now().getEpochSecond()))
                .writeString("last" + type + "URL", url)
                .write("userinfo");
        if (HASHES.size() >= Bot.getBot().getTasks().getJSONObject("profileScanning").getJSONObject("detection").getInt("maxCache")) {
            byte[] removed = HASHES.removeFirst();
            HASH_TO_POINTS.remove(removed);
            HASH_TO_OUTPUT.remove(removed);
        }
        return points/2;
    }

    @Override
    public HashMap<String, String> lookup() {
        HashMap<String, String> info = lookupBase();
        info.put("user", user.getName());
        info.put("avatarURL", avatarURL);
        info.put("bannerURL", bannerURL);
        info.put("customStatus", customStatus);
        info.put("source", source);
        return info;
    }

    private void warnModerators(User user, String flagged, String details) {
        Bot.getBot().sendDeploymentMessage("cmd", Bot.getBot().getMostModerators().getAsMention() + " Potential Offensive " + flagged + " of " + user.getAsMention() + "\n**Details:**\n> " + details);
    }
    private void warnModerators(User user, String flagged, String details, FileUpload file) {
        Bot.getBot().sendDeploymentMessage("cmd", Bot.getBot().getMostModerators().getAsMention() + " Potential Offensive " + flagged + " of " + user.getAsMention() + "\n**Details:**\n```" + details + "```", file);
    }

    private boolean basicTextCheck(@NotNull String text) {
        if (ProfileScanTask.DETECTION_TEXT == null) {
            int size = Integer.parseInt(Bot.getBot().getDeployment().get("basicWordChkList.size"));
            ProfileScanTask.DETECTION_TEXT = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ProfileScanTask.DETECTION_TEXT.add(Bot.getBot().getDeployment().get("basicWordChkList." + i));
            }
        }
        for (String detection : ProfileScanTask.DETECTION_TEXT) {
            if (text.toLowerCase().contains(detection.toLowerCase())) {
                appendOutput("Failed basic custom status chk");
                return true;
            }
        }
        appendOutput("Passed basic custom status chk");
        return false;
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

    public double compareByteArrays(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length), nLarge = Math.max(a.length, b.length);
        int unequalCount = nLarge - n;
        for (int i=0; i<n; i++)
            if (a[i] != b[i]) unequalCount++;
        return unequalCount * 100.0 / nLarge;
    }
}
