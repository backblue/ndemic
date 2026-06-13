package org.backblue.events;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.backblue.core.Bot;
import org.backblue.utilities.DefinedChannel;
import org.backblue.utilities.FeatureFlag;
import org.backblue.utilities.MessagePriority;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class CryptoDetection extends MessagePriority {

    private static final Logger Log = LoggerFactory.getLogger(CryptoDetection.class);

    final double threshold;
    final Map<String, Double> keywords;
    final Map<String, ConcurrentLinkedQueue<CryptoDetection.Bundle>> spammedChannels;
    final Set<String> flaggedUsers;
    final ITesseract tesseract = new Tesseract();

    public CryptoDetection(int priority, Bot bot, JSONObject json) {
        super(priority, bot);
        keywords = new ConcurrentHashMap<>();
        spammedChannels = new ConcurrentHashMap<>();
        flaggedUsers = ConcurrentHashMap.newKeySet();
        tesseract.setDatapath("data/tessdata");
        tesseract.setLanguage("eng");
        tesseract.setOcrEngineMode(1);
        tesseract.setPageSegMode(11);
        bot.getScheduler().scheduleAtFixedRate(this::sendAll, 1, 1, TimeUnit.MINUTES);
        if (!bot.isFeatureEnabled(FeatureFlag.DetectCrypto)) {
            threshold = 0;
        } else {
            if (json == null) {
                threshold = 0;
                Log.warn("No config provided for crypto detection, disabling");
                bot.disableFeature(FeatureFlag.DetectCrypto);
                return;
            }
            threshold = json.optDouble("threshold", 8.00);
            JSONObject obj = json.optJSONObject("keywords");
            for (String key : obj.keySet()) {
                keywords.put(key, obj.getDouble(key));
            }
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
        if (!bot.isFeatureEnabled(FeatureFlag.DetectCrypto)) {
            return false;
        }
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
            } catch (TesseractException | IOException e) {
                Log.error("Error processing image for crypto detection", e);
            }
        }
        if (userAlreadyFlagged || points >= threshold) {
            spammedChannels.computeIfAbsent(event.getAuthor().getId(), id -> new ConcurrentLinkedQueue<>())
                    .add(new Bundle(points, files));
            flaggedUsers.add(event.getAuthor().getId());
            event.getMessage().delete().queue();
            return true;
        } else for (File file : files) if (!file.delete()) Log.warn("Unable to delete safe file: {}", file.getAbsolutePath());

        return false;
    }

    public void sendAll() {
        if (spammedChannels.isEmpty()) return;
        for (String id : spammedChannels.keySet()) {
            Member member = bot.getDeploymentGuild().getMemberById(id);
            ConcurrentLinkedQueue<Bundle> bundles = spammedChannels.remove(id);
            if (member == null || bundles == null) continue;
            member.timeoutFor(6, TimeUnit.HOURS).reason("Posted suspected crypto content").queue();

            List<File> files = bundles.stream()
                    .flatMap(bundle -> bundle.attachments().stream())
                    .toList();

            Container container = Container.of(
                    TextDisplay.of("# :warning: Messages Blocked").withUniqueId((int) (Math.random() * Short.MAX_VALUE)),
                    Section.of(
                            Thumbnail.fromUrl(member.getEffectiveAvatarUrl()),
                            TextDisplay.of(member.getUser().getAsMention() + "'s messages have been flagged for spam."),
                            TextDisplay.of("## Details:\n> Sent **" + bundles.size() + "** messages containing crypto scam images.")
                    ).withUniqueId((int) (Math.random() * Short.MAX_VALUE)),

                    MediaGallery.of(
                            files.stream()
                                    .map(file -> MediaGalleryItem.fromFile(FileUpload.fromData(file)))
                                    .toList()
                    ).withUniqueId((int) (Math.random() * Short.MAX_VALUE)),

                    Separator.createDivider(Separator.Spacing.SMALL).withUniqueId(5),
                    TextDisplay.of("-# These messages have already been deleted by " + bot.getDeploymentGuild().getSelfMember().getAsMention() + ".").withUniqueId(4)

            ).withUniqueId((int) (Math.random() * Short.MAX_VALUE));
            bot.getIO().send(DefinedChannel.DeploymentBotCommands, "", container);
            for (Bundle bundle : bundles) {
                for (File file : bundle.attachments()) {
                    if (!file.delete()) Log.warn("Unable to delete file: {}", file.getAbsolutePath());
                }
            }
        }
        this.flaggedUsers.clear();
    }

    private double processImage(File file) throws TesseractException, IOException {
        long start = System.currentTimeMillis();
        double result = 0.0;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            String text = extractText(file);
            text = text.toLowerCase();
            for (String key : this.keywords.keySet()) {
                if (text.contains(key)) {
                    result += this.keywords.get(key);
                    if (result >= threshold) return result;
                }
            }
        }
        long end = System.currentTimeMillis();
        Log.info("Processed image {} in {}ms with score {}", file.getName(), (end - start), result);
        return result;
    }
    public String extractText(File file) throws TesseractException, IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new RuntimeException("Unable to decode image in message");
        }
        return tesseract.doOCR(image).strip();
    }

    record Bundle(double points, List<File> attachments) {}
}
