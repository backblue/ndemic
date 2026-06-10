package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CryptoDetection extends MessagePriority {

    private static final Logger Log = LoggerFactory.getLogger(CryptoDetection.class);

    final double threshold;
    final Map<String, Double> keywords;
    final Map<String, List<CryptoDetection.Bundle>> spammedChannels;
    final ITesseract tesseract = new Tesseract();

    public CryptoDetection(int priority, Bot bot, JSONObject json) {
        super(priority, bot);
        keywords = new ConcurrentHashMap<>();
        spammedChannels = new ConcurrentHashMap<>();
        tesseract.setDatapath("data/tessdata");
        tesseract.setLanguage("eng");
        tesseract.setOcrEngineMode(1);
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
        double points = 0.0;
        for (Message.Attachment attachment : attachmentList) {
            File file = attachment.getProxy()
                    .downloadToFile(new File("data/temp/" + attachment.getId() + "_" + attachment.getFileName()))
                    .join();
            if (!file.isFile()) continue;
            try {
                points += this.processImage(file);
            } catch (TesseractException | IOException e) {
                Log.error("Error processing image for crypto detection", e);
            } finally {
                if (!file.delete()) {
                    Log.warn("Failed to delete temp file: {}", file.getAbsolutePath());
                }
            }
        }
        if (points >= threshold) {
            List<Message.Attachment> a = event.getMessage().getAttachments();
            List<FileUpload> fileUploadList = bot.toUploads(a);
            spammedChannels.computeIfAbsent(event.getAuthor().getId(), id -> new ArrayList<>())
                    .add(new Bundle(points, fileUploadList));
            event.getMessage().delete().queue();
            return true;
        }
        return false;
    }

    public void sendAll() {
        int count = 0;
        for (String id : spammedChannels.keySet()) {
            count++;
            User user = bot.getJDA().getUserById(id);
            if (user == null) continue;
            List<Bundle> bundles = spammedChannels.remove(id);
            if (bundles == null) continue;
            MessageEmbed embed = new EmbedBuilder()
                    .setThumbnail(user.getEffectiveAvatarUrl())
                    .setTitle(":warning: Messages Blocked")
                    .setDescription(user.getAsMention() + " spammed **" + bundles.size() + "** messages with a high probability of being crypto scam.")
                    .setColor(Color.RED)
                    .setFooter("Points achieved: " + bundles.getFirst().points)
                    .build();
            bot.getIO().send(DefinedChannel.DeploymentBotCommands, "",  embed, bundles.getFirst().attachments());
        }
        if (count > 0) bot.getIO().send(DefinedChannel.DeploymentBotCommands, bot.getMostModerators().getName());

    }

    private double processImage(File file) throws TesseractException, IOException {
        double result = 0.0;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            String text = extractText(file);
            text = text.toLowerCase();
            for (String key : this.keywords.keySet()) {
                if (text.contains(key)) {
                    result += this.keywords.get(key);
                }
            }
        }
        return result;
    }
    public String extractText(File file) throws TesseractException, IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new RuntimeException("Unable to decode image in message");
        }
        tesseract.setPageSegMode(6);
        String mode6 = tesseract.doOCR(image);
        tesseract.setPageSegMode(11);
        String mode11 = tesseract.doOCR(image);
        return (mode6.length() > mode11.length()) ? mode6 : mode11;
    }

    record Bundle(double points, List<FileUpload> attachments) {}
}
