package org.backblue.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class CryptoDetection extends MessagePriority {

    private static final Logger Log = LoggerFactory.getLogger(CryptoDetection.class);

    final double threshold;
    final Map<String, Double> keywords;
    final Map<String, ConcurrentLinkedQueue<CryptoDetection.Bundle>> spammedChannels;
    final Set<String> flaggedUsers;
    final ITesseract tesseract;
    AtomicBoolean sendInProgress = new AtomicBoolean(false);

    public CryptoDetection(int priority, Bot bot, JSONObject json) {
        super(priority, bot);
        if (!bot.isFeatureEnabled(FeatureFlag.DetectCrypto)) {
            threshold = 0;
            tesseract = null;
            keywords = null;
            spammedChannels = null;
            flaggedUsers = null;
        } else {
            if (json == null) {
                keywords = null;
                spammedChannels = null;
                flaggedUsers = null;
                threshold = 0;
                tesseract = null;
                Log.warn("No config provided for crypto detection, disabling");
                bot.disableFeature(FeatureFlag.DetectCrypto);
                return;
            }
            keywords = new ConcurrentHashMap<>();
            spammedChannels = new ConcurrentHashMap<>();
            flaggedUsers = ConcurrentHashMap.newKeySet();
            threshold = json.optDouble("threshold", 8.00);
            JSONObject obj = json.optJSONObject("keywords");
            for (String key : obj.keySet()) {
                keywords.put(key, obj.getDouble(key));
            }
            tesseract = new Tesseract();
            tesseract.setDatapath("data/tessdata");
            tesseract.setLanguage("eng");
            tesseract.setOcrEngineMode(1);
            tesseract.setPageSegMode(11);

            bot.getScheduler().scheduleAtFixedRate(this::sendAll, 1, 1, TimeUnit.MINUTES);
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
        this.sendInProgress.set(true);
        for (String id : spammedChannels.keySet()) {
            Member member = bot.getDeploymentGuild().getMemberById(id);
            ConcurrentLinkedQueue<Bundle> bundles = spammedChannels.remove(id);
            if (member == null || bundles == null) continue;
            this.bot.timeout(member, "Posted crypto messages", 6, TimeUnit.HOURS);

            List<File> files = bundles.stream()
                    .flatMap(bundle -> bundle.attachments().stream())
                    .toList();

            bot.getIO().send(DefinedChannel.DeploymentBotCommands, "", bot.getInteractive().createSpam(member, files));
            for (Bundle bundle : bundles) {
                for (File file : bundle.attachments()) {
                    if (!file.delete()) Log.warn("Unable to delete file: {}", file.getAbsolutePath());
                }
            }
        }
        this.sendInProgress.set(false);
        this.flaggedUsers.clear();
    }

    private double processImage(File file) throws TesseractException, IOException {
        long start = System.currentTimeMillis();
        double result = 0.0;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            String text = extractText(file);
            if (text == null) return 0.0;
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
        if (this.sendInProgress.get()) return;
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
}
