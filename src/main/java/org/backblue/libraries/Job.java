package org.backblue.libraries;

import com.azure.ai.contentsafety.models.AnalyzeImageOptions;
import com.azure.ai.contentsafety.models.AnalyzeImageResult;
import com.azure.ai.contentsafety.models.ContentSafetyImageData;
import com.azure.ai.contentsafety.models.ImageCategoriesAnalysis;
import com.azure.core.util.BinaryData;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.backblue.Core;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class Job {

    public final static Queue<Job> QUEUE = new LinkedList();
    public final static ArrayList<Job> RECENT_COMPLETE_JOBS = new ArrayList<>();

    private String name;
    private String desc;
    private String type;
    private String output = "\n";

    @Override
    public String toString() {return name + " *(" + type + ")*";}
    public String toStringFull() {return name + " / `" + desc + "` / " + type;}
    public Job(String name, String desc, String type) {
        this.name = name;
        this.desc = desc;
        this.type = type;
        if (type.equals("profileScan")) {
            JSONObject data = UserJSON.readUserJSON(name);
            if (data.has("lastProfileScan")) {
                long lastScan = Long.parseLong(data.getString("lastProfileScan"));
                if (lastScan + 600 < Instant.now().getEpochSecond()) {
                    return;
                }
            }
        }
        QUEUE.add(this);
    }

    private void markInvalid() {
        this.type = this.type + ":exclamation:";
    }
    private void markDone() {this.type = this.type + " done: <t:" + Instant.now().getEpochSecond() + ":R>";}

    public static void process() {
        if (QUEUE.isEmpty()) {
            return;
        }
        Job job = QUEUE.peek();
        if (job.type.equals("profileScan")) {
            if (job.processImage() == null) {
                System.out.println("Failed to process job, either due to an invalid image, or rate limited by the API. Check console!");
                return;
            } else if (job.processImage()) {
                TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));
                Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("role.mod"));
                channel.sendMessage(pingRole.getAsMention() + "\n" + Core.BOT.getSelfUser().getAsMention() + " thinks this profile is inappropriate: <@" + job.name + ">\n" + job.desc + "\n-# Trace: " + job.output).queue();
            }
        } else {
            job.markInvalid();
        }
        job.markDone();
        QUEUE.poll();
        RECENT_COMPLETE_JOBS.add(job);
        if (RECENT_COMPLETE_JOBS.size() > 5) {
            RECENT_COMPLETE_JOBS.removeFirst();
        }
    }

    /*
    * Thanks to StackOverFlow for this method to download a URL (img) and convert it as a byte array.
    */
    private byte[] downloadUrl(URL toDownload) {
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
            System.out.println("Error converting URL->BYTE");
            return null;
        }
        return outputStream.toByteArray();
    }

    private Boolean processImage() {

        ContentSafetyImageData image = new ContentSafetyImageData();
        byte[] imageData;

        try {
            imageData = downloadUrl(new URL(this.desc));
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL Data! URL Attempted: " + this.desc);
            return null;
        }

        image.setContent(BinaryData.fromBytes(imageData));
        AnalyzeImageResult response = Core.CONTENT_SAFETY_CLIENT.analyzeImage(new AnalyzeImageOptions(image));
        HashMap<String, Integer> categories = new HashMap<>();
        for (ImageCategoriesAnalysis result : response.getCategoriesAnalysis()) {
            categories.put(result.getCategory().toString(), result.getSeverity());
        }
        this.output = categories.toString();

        UserJSON.get(this.name).writeString("lastProfileScan", String.valueOf(Instant.now().getEpochSecond())).write();

        TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.log"));

        System.out.println("Successfully scanned profile of: " + this.name + " with results: " + this.output);

        String fail = ":x: **FAIL**: Processed Profile of: <@" + this.name + "> with problems.";

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
        } else {channel.sendMessage(":white_check_mark: PASS: Processed Profile of: <@" + this.name + ">"); return false;}
    }
}
