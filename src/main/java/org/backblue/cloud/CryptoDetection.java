package org.backblue.cloud;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.backblue.core.Bot;
import org.backblue.enums.SetChannel;
import org.backblue.enums.Feature;
import org.backblue.extension.LiveFramework;
import org.backblue.utilities.MessagePriority;

import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CryptoDetection extends MessagePriority implements LiveFramework.ButtonReturn {

    private static final Logger Log = LoggerFactory.getLogger(CryptoDetection.class);
    private static final int FOOTER_NOTE = 101;

    final double threshold;
    final Map<String, Double> keywords;
    final Map<String, PendingReport> pendingReports;
    final Set<String> flaggedUsers;
    final ITesseract tesseract;
    final CloudOCR azure;
    final boolean inviteCheck;
    final Pattern inviteRegex = Pattern.compile("(?i)(discord(?:app)?.gg(?:/|\\+/+)|discord(?:app)?.com(?:/|\\+/+)invite/)([A-z0-9-]{2,})");

    public CryptoDetection(int priority, Bot bot, JSONObject json, String endpoint, String secret) {
        super(priority, bot);
        if (!bot.isFeatureEnabled(Feature.DetectCrypto) || json == null) {
            threshold = 0;
            tesseract = null;
            keywords = null;
            pendingReports = null;
            flaggedUsers = null;
            azure = null;
            inviteCheck = false;
            if (json == null) Log.warn("No config provided for crypto detection, disabling");
            bot.disableFeature(Feature.DetectCrypto);
        } else {
            keywords = new ConcurrentHashMap<>();
            pendingReports = new ConcurrentHashMap<>();
            flaggedUsers = ConcurrentHashMap.newKeySet();
            threshold = json.optDouble("threshold", 8.00);
            inviteCheck = json.optBoolean("inviteCheck", false);
            boolean local = json.optBoolean("fallbackOnLocal", true);
            JSONObject obj = json.optJSONObject("keywords");
            for (String key : obj.keySet()) keywords.put(key, obj.getDouble(key));

            if (local) {
                tesseract = new Tesseract();
                tesseract.setDatapath("data/tessdata");
                tesseract.setLanguage("eng");
                tesseract.setOcrEngineMode(1);
                tesseract.setPageSegMode(11);
            } else {
                tesseract = null;
            }
            boolean useCloud = json.optBoolean("useCloud", true);
            if (useCloud) {
                if (endpoint == null || secret == null) {
                    azure = null;
                    Log.warn("OCR provider requires credentials, disabling");
                    return;
                }
                azure = new CloudOCR(endpoint, secret);
            } else {
                azure = null;
            }

            if (tesseract == null && azure == null) {
                Log.error("No OCR provider selected, disabling");
                bot.disableFeature(Feature.DetectCrypto);
                return;
            }

            bot.getScheduler().scheduleAtFixedRate(this::cleanup, 20, 20, TimeUnit.MINUTES);
        }
    }

    /**
     * In order, determined by priority, to see what events should be fired first.
     *
     * @param event {@code MessageReceivedEvent} event.
     * @return {@code true} if event is 'canceled', then no other listener that has priority above the current will receive this event.
     */
    @Override
    public boolean cancelled(MessageReceivedEvent event) {
        if (!bot.isFeatureEnabled(Feature.DetectCrypto)) {
            return false;
        }
        if (event.getMember() != null && event.getMember().hasPermission(Permission.ADMINISTRATOR)) return false;
        List<Message.Attachment> attachmentList = event.getMessage().getAttachments();
        List<File> files = new ArrayList<>();
        double points = 0.0;
        boolean userAlreadyFlagged = this.flaggedUsers.contains(event.getAuthor().getId());

        for (Message.Attachment attachment : attachmentList) {
            if (!attachment.isImage()) continue;

            File file = attachment.getProxy()
                    .downloadToFile(new File("data/temp/" + attachment.getId() + "_" + attachment.getFileName()))
                    .join();
            if (!file.isFile()) continue;
            files.add(file);

            if (userAlreadyFlagged) {
                continue;
            }

            try {
                points += this.processImage(file);
                if (points >= threshold) break;
            } catch (Exception e) {
                Log.error("Error processing image for crypto detection", e);
            }
        }
        if (userAlreadyFlagged || points >= threshold) {
            PendingReport report = pendingReports.computeIfAbsent(
                    event.getAuthor().getId(),
                    id -> new PendingReport());
            report.bundles.add(new Bundle(points, files));
            if (report.scheduled.compareAndSet(false, true)) {
                bot.getScheduler().schedule(
                        () -> sendReport(event.getAuthor().getId()),
                        1,
                        TimeUnit.MINUTES);
            }

            flaggedUsers.add(event.getAuthor().getId());
            event.getMessage().delete().queue();
            return true;
        } else for (File file : files) if (!file.delete()) Log.warn("Unable to delete safe file: {}", file.getAbsolutePath());

        return false;
    }

    private void sendReport(String userId) {
        PendingReport report = pendingReports.remove(userId);
        if (report == null) return;

        Member member = bot.getDeploymentGuild().getMemberById(userId);
        if (member == null) return;
        List<File> files = report.bundles.stream()
                .flatMap(bundle -> bundle.attachments().stream())
                .toList();

        bot.getIO().send(SetChannel.DeploymentBotCommands, "", createContainer(member, files), this);
        for (Bundle bundle : report.bundles) {
            for (File file : bundle.attachments()) {
                if (!file.delete()) Log.warn("Unable to delete file: {}", file.getAbsolutePath());
            }
        }

        flaggedUsers.remove(userId);
    }

    private Container createContainer(Member member, List<File> attachments) {
        return Container.of(
                TextDisplay.of("# :warning: Messages Blocked"),
                Section.of(
                        Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                        TextDisplay.of(member.getUser().getAsMention() + "'s messages have been flagged for spam."),
                        TextDisplay.of("## Details:\n> Sent **" + attachments.size() + "** images containing scams/invites.")
                ),
                MediaGallery.of(
                        attachments.stream()
                                .map(file -> MediaGalleryItem.fromFile(FileUpload.fromData(file)))
                                .toList()
                ),
                ActionRow.of(
                        Button.danger(identifier() + ";kick;" + member.getId(), "Softban"),
                        Button.danger(identifier() + ";ban;" + member.getId(), "Ban"),
                        Button.secondary(identifier() + ";nothing", "Do nothing")
                ),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# These messages have already been deleted.").withUniqueId(FOOTER_NOTE)
        );
    }

    @Override
    public Container onButton(@NonNull ButtonInteractionEvent event, String... actions) {
        if (event.getMember() == null) return null;
        String action = actions[1];
        if ((action.equals("kick") || action.equals("ban")) && actions.length > 2) {
            Member member = bot.getDeploymentGuild().getMemberById(actions[2]);
            if (member != null) bot.getEZPunish().ezPunish(member, event.getMember(), List.of("ezpunish:spam"), action.equals("ban"), member.getEffectiveAvatarUrl(), null);
        }
        return event.getMessage().getComponents().getFirst().asContainer()
                .replace(ComponentReplacer.byUniqueId(FOOTER_NOTE, TextDisplay.of("-# Action taken <t:" + Instant.now().getEpochSecond() + ":R> by `" + event.getMember().getEffectiveName() + "` to **" + event.getButton().getLabel().toLowerCase() + "**.")))
                .asDisabled();
    }

    private double processImage(File file) throws TesseractException, IOException, InterruptedException {
        long start = System.currentTimeMillis();
        double result = 0.0;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            String text = extractTextWrapper(file);
            if (text == null) return 0.0;
            text = text.toLowerCase();
            for (String key : this.keywords.keySet()) {
                if (text.contains(key)) {
                    result += this.keywords.get(key);
                }
            }
            if (this.inviteCheck) {
                Matcher matcher = this.inviteRegex.matcher(text);
                if (matcher.find()) result += this.threshold;
            }
        }
        long end = System.currentTimeMillis();
        Log.info("Processed image {} in {}ms with score {}", file.getName(), (end - start), result);
        return result;
    }

    public String extractTextWrapper(File file) throws TesseractException, IOException, InterruptedException {
        if (azure != null && azure.enabled()) return azure.extractText(file);
        if (tesseract != null) return extractText(file);
        return "";
    }

    public String extractText(File file) throws TesseractException, IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            Log.error("Unable to decode image: {}", file.getName());
            return null;
        }
        try {
            synchronized (tesseract) {return tesseract.doOCR(image).strip();}
        } finally {
            image.flush();
        }
    }

    public void cleanup() {
        Path dir = Paths.get("data/temp");
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".png")
                                || name.endsWith(".jpg")
                                || name.endsWith(".jpeg")
                                || name.endsWith(".gif")
                                || name.endsWith(".webp")
                                || name.endsWith(".bmp");
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            Log.error("Failed to clean unused temporary file: ", e);
                        }
                    });
        } catch (IOException e) {
            Log.error("Directory error ", e);
        }
    }

    record Bundle(double points, List<File> attachments) {}
    private static class PendingReport {
        final ConcurrentLinkedQueue<Bundle> bundles = new ConcurrentLinkedQueue<>();
        final AtomicBoolean scheduled = new AtomicBoolean(false);
    }
}
