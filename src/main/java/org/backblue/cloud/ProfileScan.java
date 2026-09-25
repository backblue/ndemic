package org.backblue.cloud;

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
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.backblue.enums.SetChannel;
import org.backblue.enums.Feature;
import org.backblue.extension.LiveFramework;
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
import java.time.Instant;
import java.time.OffsetTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileScan extends ListenerAdapter implements LiveFramework.ButtonReturn {

    private static final Logger Log = LoggerFactory.getLogger(ProfileScan.class);
    private static final int MAX_SCAN_CACHE_SIZE = 24;
    private static final int FOOTER_NOTE = 101;

    final Bot bot;
    final ContentSafetyClient safetyClient;
    final int scanCooldownAfterFlagging;
    final int hateMinToAlert;
    final boolean ping;
    final Map<String, OffsetTime> lastScan;

    public ProfileScan(Bot bot, String endpoint, String key, JSONObject config) {
        this.bot = bot;
        this.lastScan = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_SCAN_CACHE_SIZE;
            }
        };
        if (key == null || endpoint == null) {
            Log.error("Cannot read Azure endpoint/key values, disabling");
            safetyClient = null;
            scanCooldownAfterFlagging = 0;
            hateMinToAlert = 0;
            ping = false;
            this.bot.disableFeature(Feature.ScanProfiles);
            return;
        }
        this.safetyClient = new ContentSafetyClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildClient();
        if (config == null) {
            Log.info("Missing configurations, set default values");
            scanCooldownAfterFlagging = 10;
            hateMinToAlert = 2;
            ping = true;
        } else {
            scanCooldownAfterFlagging = config.optInt("cooldownBetweenScans", 10);
            hateMinToAlert = config.optInt("hateMinToAlert", 2);
            ping = config.optBoolean("pingMods", true);
        }
    }

    public void scan(Member member) {
        if (!bot.isFeatureEnabled(Feature.ScanProfiles)
                || member == null
                || member.hasPermission(Permission.ADMINISTRATOR)
                || !OffsetTime.now().isAfter(lastScan.getOrDefault(member.getId(), OffsetTime.MIN).plusMinutes(scanCooldownAfterFlagging))) {
            return;
        }
        ScanResult avatar = scan(member.getId(), member.getEffectiveAvatarUrl());
        if (avatar != null && avatar.points >= this.hateMinToAlert) {
            bot.getIO().send(SetChannel.DeploymentBotCommands, this.ping ? bot.getMostModerators().getAsMention() : "", createContainer(member, "picture", avatar.points), this);
            lastScan.put(member.getId(), OffsetTime.now());
        }
        member.getUser().retrieveProfile().queue(profile -> {
            String bannerUrl = profile.getBannerUrl();

            if (bannerUrl != null) {
                ScanResult banner = scan(member.getId(), bannerUrl);
                if (banner != null && banner.points() >= hateMinToAlert) {
                    bot.getIO().send(SetChannel.DeploymentBotCommands, "", createContainer(member, "banner", banner.points), this);
                    lastScan.put(member.getId(), OffsetTime.now());
                }
            }
        });
    }

    private Container createContainer(Member member, String type, int points) {
        return Container.of(
                TextDisplay.of("# :triangular_flag_on_post: Moderator Review Recommended"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of(member.getUser().getAsMention() + "'s profile " + type + " is flagged for further review."),
                        TextDisplay.of("## Details: " + member.getUser().getAsMention() + "\n> Severity value is " + points)
                ),
                ActionRow.of(
                        Button.danger(identifier() + ";kick;" + member.getId(), "Softban"),
                        Button.danger(identifier() + ";ban;" + member.getId(), "Ban"),
                        Button.secondary(identifier() + ";nothing", "Do nothing")
                ),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# Punishment actions will notify the user (and logged).").withUniqueId(FOOTER_NOTE)
        );
    }

    @Override
    public Container onButton(@NotNull ButtonInteractionEvent event, String... actions) {
        if (event.getMember() == null) return null;
        String action = actions[1];
        if ((action.equals("kick") || action.equals("ban")) && actions.length > 2) {
            Member member = bot.getDeploymentGuild().getMemberById(actions[2]);
            if (member != null) bot.getEZPunish().ezPunish(member, event.getMember(), List.of("ezpunish:profile"), action.equals("ban"), member.getEffectiveAvatarUrl(), null);
        }
        return event.getMessage().getComponents().getFirst().asContainer()
                .replace(ComponentReplacer.byUniqueId(FOOTER_NOTE, TextDisplay.of("-# Action taken <t:" + Instant.now().getEpochSecond() + ":R> by `" + event.getMember().getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")))
                .asDisabled();
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
        this.bot.getIO().send(SetChannel.DebugImageDump, id, List.of(imageFile));

        ByteArrayInputStream bais = new ByteArrayInputStream(downloadedImage);
        BufferedImage img;
        try {
            img = ImageIO.read(bais);
            if (img == null) {
                Log.info("Unable to decode image for scan of {}", id);
                return null;
            }
            int width = img.getWidth();
            int height = img.getHeight();
            if (width < 50 || height < 50) {
                Log.info("Did not scan {} due to resolution too small", id);
                img.flush();
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
                this.bot.disableFeature(Feature.ScanProfiles);
                bot.getIO().send(SetChannel.DebugAutoModAlert, "Profile scanning cannot continue. `" + e.getMessage() +" `");
            } else {
                bot.getIO().send(SetChannel.DebugAutoModAlert, "Failed to analyze image for `(" + id + ")` due to: " + e.getMessage());
            }
            img.flush();
            return null;
        } finally {
            img.flush();
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
            int totalBytes = 0;
            final int MAX_IMAGE_SIZE = 50_000_000; // 50MB limit per image
            InputStream stream = toDownload.openStream();

            while ((bytesRead = stream.read(chunk)) > 0) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_IMAGE_SIZE) {
                    Log.warn("Image {} exceeds max size limit ({} MB)", url, MAX_IMAGE_SIZE / 1_000_000);
                    stream.close();
                    return new byte[0];
                }
                outputStream.write(chunk, 0, bytesRead);
            }
            outputStream.close();
            stream.close();
        } catch (Exception e) {
            return new byte[0];
        }
        return outputStream.toByteArray();
    }
    public record ScanResult(int points) {}
}