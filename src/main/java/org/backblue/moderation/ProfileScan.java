package org.backblue.moderation;

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
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.cloud.GoogleVision;
import org.backblue.core.Bot;
import org.backblue.enums.SetChannel;
import org.backblue.enums.Feature;
import org.backblue.extension.LiveFramework;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfileScan extends ListenerAdapter implements LiveFramework.ButtonReturn {

    private static final Logger Log = LoggerFactory.getLogger(ProfileScan.class);
    private static final int MAX_SCAN_CACHE_SIZE = 24;
    private static final int FOOTER_NOTE = 101;
    private static final int CONTEXT_NOTE = 102;
    private static final int MAX_CONTEXT_PAGES = 5;
    private static final int MAX_CONTEXT_ENTITIES = 5;

    final Bot bot;
    final ContentSafetyClient safetyClient;
    // Optional: when set, flagged images are looked up on the web to give moderators context.
    final @Nullable GoogleVision vision;
    // Dedicated thread for web lookups, so slow responses never hold up the bot's shared scheduler.
    final @Nullable ExecutorService lookupExecutor;
    final int scanCooldownAfterFlagging;
    final int hateMinToAlert;
    final boolean ping;
    final Map<String, OffsetTime> lastScan;

    public ProfileScan(Bot bot, String endpoint, String key, @Nullable String googleVisionKey, JSONObject config) {
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
            vision = null;
            lookupExecutor = null;
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
        if (googleVisionKey == null || googleVisionKey.isBlank()) {
            Log.info("No GOOGLE_VISION_KEY set, flagged images will not be looked up");
            vision = null;
            lookupExecutor = null;
        } else {
            vision = new GoogleVision(googleVisionKey);
            lookupExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ProfileScan-Lookup");
                thread.setDaemon(true);
                return thread;
            });
        }
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
        bot.getProfileScans().submit(() -> scanMember(member));
    }

    private void scanMember(Member member) {
        if (!bot.isFeatureEnabled(Feature.ScanProfiles)
                || member == null
                || member.hasPermission(Permission.ADMINISTRATOR)
                || !OffsetTime.now().isAfter(lastScan.getOrDefault(member.getId(), OffsetTime.MIN).plusMinutes(scanCooldownAfterFlagging))) {
            return;
        }
        ScanResult avatar = scan(member.getId(), member.getEffectiveAvatarUrl());
        if (avatar != null && avatar.points >= this.hateMinToAlert) {
            alert(member, "picture", avatar, this.ping ? bot.getMostModerators().getAsMention() : "");
            lastScan.put(member.getId(), OffsetTime.now());
        }
        member.getUser().retrieveProfile().queue(profile -> bot.getProfileScans().submit(() -> {
            String bannerUrl = profile.getBannerUrl();

            if (bannerUrl != null) {
                ScanResult banner = scan(member.getId(), bannerUrl);
                if (banner != null && banner.points() >= hateMinToAlert) {
                    alert(member, "banner", banner, "");
                    lastScan.put(member.getId(), OffsetTime.now());
                }
            }
        }));
    }

    // Posts the review panel, then (if Google Vision is configured) fills in the lookup context in the background.
    private void alert(Member member, String type, ScanResult result, String pingText) {
        bot.getIO().send(SetChannel.DeploymentBotCommands, pingText, createContainer(member, type, result.points()), this,
                lookupExecutor == null ? null : message -> lookupExecutor.execute(() -> fillContext(message, result.image())));
    }

    private Container createContainer(Member member, String type, int points) {
        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of("# :triangular_flag_on_post: Moderator Review Recommended"));
        components.add(Section.of(
                Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                TextDisplay.of(member.getUser().getAsMention() + "'s profile " + type + " is flagged for further review."),
                TextDisplay.of("## Details: " + member.getUser().getAsMention() + "\n> Severity value is " + points)
        ));
        if (vision != null) {
            components.add(TextDisplay.of("## Image Lookup\n-# Looking up this image online...").withUniqueId(CONTEXT_NOTE));
        }
        components.add(ActionRow.of(
                Button.danger(identifier() + ";kick;" + member.getId(), "Softban"),
                Button.danger(identifier() + ";ban;" + member.getId(), "Ban"),
                Button.secondary(identifier() + ";nothing", "Do nothing")
        ));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        components.add(TextDisplay.of("-# Punishment actions will notify the user (and logged).").withUniqueId(FOOTER_NOTE));
        return Container.of(components);
    }

    private void fillContext(Message message, byte[] image) {
        String context;
        try {
            context = formatContext(vision.webDetection(image, 10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            Log.warn("Context lookup failed for flagged image", e);
            context = "-# Lookup failed: " + escape(String.valueOf(e.getMessage()));
        }
        TextDisplay section = TextDisplay.of("## Image Lookup\n" + context).withUniqueId(CONTEXT_NOTE);
        // Re-read the message so an action a moderator took meanwhile (disabled buttons, footer) is kept.
        message.getChannel().retrieveMessageById(message.getId()).queue(latest ->
                latest.editMessageComponents(latest.getComponents().getFirst().asContainer()
                                .replace(ComponentReplacer.byUniqueId(CONTEXT_NOTE, section)))
                        .useComponentsV2()
                        .queue());
    }

    static String formatContext(GoogleVision.WebContext context) {
        StringBuilder text = new StringBuilder();
        if (!context.bestGuessLabels().isEmpty()) {
            text.append("**Best guess:** ").append(escape(String.join(", ", context.bestGuessLabels()))).append('\n');
        }
        List<String> entities = context.entities().stream().limit(MAX_CONTEXT_ENTITIES).map(e -> escape(e.description())).toList();
        if (!entities.isEmpty()) {
            text.append("**Related:** ").append(String.join(", ", entities)).append('\n');
        }
        List<GoogleVision.Page> pages = context.pages().stream()
                .filter(page -> page.url().length() <= 300)
                .limit(MAX_CONTEXT_PAGES)
                .toList();
        if (!pages.isEmpty()) {
            text.append("**Found on:**\n");
            for (GoogleVision.Page page : pages) text.append("- ").append(link(page)).append('\n');
        }
        int exact = context.fullMatches().size();
        int partial = context.partialMatches().size();
        if (exact + partial > 0) {
            text.append("-# *").append(exact).append(" exact and ").append(partial).append(" partial copies found online.*");
        }
        return text.isEmpty() ? "-# No matches found online." : text.toString().strip();
    }

    private static String link(GoogleVision.Page page) {
        String title = page.title().isBlank() ? URI.create(page.url()).getHost() : page.title();
        if (title == null) title = page.url();
        if (title.length() > 80) title = title.substring(0, 77) + "...";
        String url = page.url().replace("(", "%28").replace(")", "%29").replace(" ", "%20");
        return "[" + escape(title) + "](" + url + ")";
    }

    private static String escape(String text) {
        return text.replaceAll("([\\\\*_`~|\\[\\]])", "\\\\$1");
    }

    @Override
    public Container onButton(@NotNull ButtonInteractionEvent event, String... actions) {
        if (event.getMember() == null) return null;
        // Replying acknowledges the interaction, so LiveContainer leaves the review open for another moderator.
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need the **Ban Members** permission to act on this review.").setEphemeral(true).queue();
            return null;
        }
        String action = actions[1];
        if ((action.equals("kick") || action.equals("ban")) && actions.length > 2) {
            Member member = bot.getDeploymentGuild().getMemberById(actions[2]);
            if (member == null) {
                event.reply("That member is no longer in the server.").setEphemeral(true).queue();
                return null;
            }
            String result = bot.getEZPunish().ezPunish(member, event.getMember(), List.of("ezpunish:profile"), action.equals("ban"), member.getEffectiveAvatarUrl(), null);
            // ezPunish reports refusals (e.g. a protected target) only through its message.
            if (!result.startsWith("User has been")) {
                event.reply(result).setEphemeral(true).queue();
                return null;
            }
        }
        return event.getMessage().getComponents().getFirst().asContainer()
                .replace(ComponentReplacer.byUniqueId(FOOTER_NOTE, TextDisplay.of("-# Action taken <t:" + Instant.now().getEpochSecond() + ":R> by `" + event.getMember().getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")))
                .asDisabled();
    }

    @Override
    public void onUserUpdateAvatar(@NotNull UserUpdateAvatarEvent event) {
        scan(bot.getDeploymentGuild().getMember(event.getUser()));
    }
    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        scan(event.getMember());
    }
    @Override
    public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {
        scan(event.getMember());
    }

    private ScanResult scan(String id, String url) {
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
        return new ScanResult(hateLevel, downloadedImage);
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
    public record ScanResult(int points, byte[] image) {}
}
