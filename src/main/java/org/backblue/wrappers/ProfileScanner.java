package org.backblue.wrappers;

import com.azure.ai.contentsafety.ContentSafetyClient;
import com.azure.ai.contentsafety.ContentSafetyClientBuilder;
import com.azure.ai.contentsafety.models.AnalyzeImageOptions;
import com.azure.ai.contentsafety.models.AnalyzeImageResult;
import com.azure.ai.contentsafety.models.ContentSafetyImageData;
import com.azure.ai.contentsafety.models.ImageCategoriesAnalysis;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.FeatureFlag;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.OffsetTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileScanner extends ListenerAdapter {

    private static final Logger Log = LoggerFactory.getLogger(ProfileScanner.class);

    final Bot bot;
    final ContentSafetyClient safetyClient;
    final int cooldownBetweenScans;
    final int hateMinToAlert;
    final EZPunishProfileScan hook;
    final Map<String, OffsetTime> lastScan = new HashMap<>();

    public ProfileScanner(Bot bot, EZPunishProfileScan hook, String endpoint, String key, JSONObject config) {
        this.bot = bot;
        this.hook = hook;
        if (key == null || endpoint == null) {
            Log.error("Cannot read Azure endpoint/key values, disabling");
            safetyClient = null;
            cooldownBetweenScans = 0;
            hateMinToAlert = 0;
            this.bot.disableFeature(FeatureFlag.ScanProfiles);
            return;
        }
        this.safetyClient = new ContentSafetyClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildClient();
        if (config == null) {
            Log.info("Missing configurations, set default values");
            cooldownBetweenScans = 10;
            hateMinToAlert = 2;
        } else {
            cooldownBetweenScans = config.optInt("cooldownBetweenScans", 10);
            hateMinToAlert = config.optInt("hateMinToAlert", 2);
        }
    }

    public void scan(Member member) {
        if (!bot.isFeatureEnabled(FeatureFlag.ScanProfiles)
                || member == null
                || member.hasPermission(Permission.ADMINISTRATOR)
                || !OffsetTime.now().isAfter(lastScan.getOrDefault(member.getId(), OffsetTime.MIN).plusMinutes(cooldownBetweenScans))) {
            return;
        }
        ScanResult avatar = scan(member.getId(), member.getEffectiveAvatarUrl());
        if (avatar != null && avatar.points >= this.hateMinToAlert) {
            bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getAsMention(), hook.create(member, "picture", avatar.points));
        }
        member.getUser().retrieveProfile().queue(profile -> {
            String bannerUrl = profile.getBannerUrl();

            if (bannerUrl != null) {
                ScanResult banner = scan(member.getId(), bannerUrl);
                if (banner != null && banner.points() >= hateMinToAlert) {
                    bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getAsMention(), hook.create(member, "banner", banner.points));
                }
            }
        });
        lastScan.put(member.getId(), OffsetTime.now());
    }

    @Override
    public void onUserUpdateAvatar(@NotNull UserUpdateAvatarEvent event) {
        Member member = bot.getDeploymentGuild().getMember(event.getUser());
        scan(member);
    }
    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        scan(event.getMember());
    }
    @Override
    public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {
        scan(event.getMember());
    }
    public ScanResult scan(String id, String url) {
        byte[] downloadedImage = downloadUrl(url);
        if (downloadedImage.length == 0) {
            return null;
        }
        FileUpload imageFile = FileUpload.fromData(downloadedImage, id + ".png");
        this.bot.getIO().send(DefinedChannel.DebugImageDump, id, null, List.of(imageFile));

        ByteArrayInputStream bais = new ByteArrayInputStream(downloadedImage);
        BufferedImage img;
        try {
            img = ImageIO.read(bais);
            if (img == null) {
                Log.info("Unable to decode image format for scan of {}", id);
                return null;
            }
            int width = img.getWidth();
            int height = img.getHeight();
            if (width < 50 || height < 50) {
                Log.info("Did not scan {} due to resolution too small", id);
                return null;
            }
        } catch (Exception e) {
            Log.info("Unable to determine width & height for scan of {}", id);
            return null;
        }
        ContentSafetyImageData image = new ContentSafetyImageData();
        image.setContent(BinaryData.fromBytes(downloadedImage));
        AnalyzeImageResult response;
        try {
            response = this.safetyClient.analyzeImage(new AnalyzeImageOptions(image));
        } catch (HttpResponseException e) {
            if (e.getResponse().getStatusCode() == 403) {
                this.bot.disableFeature(FeatureFlag.ScanProfiles);
                bot.getIO().send(DefinedChannel.DebugAutoModAlert, "Profile scanning cannot continue. `" + e.getMessage() +" `");
            } else {
                bot.getIO().send(DefinedChannel.DebugAutoModAlert, "Failed to analyze image for `(" + id + ")` due to: " + e.getMessage());
            }
            return null;
        }
        int hateLevel = 0;
        for (ImageCategoriesAnalysis analysis : response.getCategoriesAnalysis()) {
            if (analysis.getCategory().toString().equals("Hate") && analysis.getSeverity() > 0) {
                hateLevel = analysis.getSeverity();
                break;
            }
        }
        return new ScanResult(hateLevel);
    }
    private byte[] downloadUrl(String url) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        URL toDownload;
        try {
            toDownload = URI.create(url).toURL();
            byte[] chunk = new byte[4096];
            int bytesRead;
            InputStream stream = toDownload.openStream();

            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }
            outputStream.close();
            stream.close();
        } catch (Exception e) {
            Log.warn("Unable to download image due to: {}", e.getMessage());
            return new byte[0];
        }
        return outputStream.toByteArray();
    }
    public record ScanResult(int points) {}
}