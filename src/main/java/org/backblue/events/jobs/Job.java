package org.backblue.events.jobs;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.backblue.Core;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public abstract class Job {

    private static int counter = 0;
    public final static Queue<Job> QUEUE = new LinkedList<>();
    public final static Stack<Job> RECENT_COMPLETE_JOBS = new Stack<>();
    public final static HashMap<Integer, Job> ID_TO_JOB = new HashMap<>();
    private String output = "\n";
    private final Long created;
    protected Long started;
    private Long completed;
    public int id;

    public static Job search(int idToLookFor) {
        try {
            return ID_TO_JOB.get(idToLookFor);
        } catch (Exception e) {
            return null;
        }
    }

    public final String getOutput() {
        return output;
    }

    public final void appendOutput(String output) {
        this.output += output;
    }

    protected Job() {
        this.id = counter++;
        ID_TO_JOB.put(this.id, this);
        this.created = System.currentTimeMillis();
    }

    public final Long markInvalid() {
        this.output = this.output + ":x:";
        return completed = System.currentTimeMillis();
    }

    public static int getCounter() {
        return counter;
    }

    public final Long markDone() {
        this.output = this.output + " OK: <t:" + System.currentTimeMillis() / 1000 + ":R>";
        return completed = System.currentTimeMillis();

    }

    public final Long markInvalid(String reason) {
        this.output = this.output + ":x: " + reason;
        return completed = System.currentTimeMillis();
    }

    public final Long markDoneWithPrejudice(String reason) {
        this.output = this.output + " :warning: <t:" + System.currentTimeMillis() / 1000 + ":R> " + reason;
        return completed = System.currentTimeMillis();
    }

    public final void ignore() {
        QUEUE.remove(this);
    }

    /*
     * Thanks to StackOverFlow for this method to download a URL (img) and convert it as a byte array.
     */
    protected final byte[] downloadUrl(URL toDownload) throws MalformedURLException {
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

    public abstract void process();
    public HashMap<String, String> lookup() {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(this.id));
        map.put("output", this.output);
        map.put("created", String.valueOf(this.created));
        map.put("started", String.valueOf(this.started));
        map.put("completed", String.valueOf(this.completed));
        map.put("type", this.getClass().getSimpleName());
        return map;
    }

    @Override
    public abstract String toString();

    public final void log() {
        HashMap<String, String> jobData = lookup();
        EmbedBuilder raw = new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("Job ID: `" + id + "`");
        for (String key : jobData.keySet()) {
            if (jobData.get(key) == null) {
                raw.addField(key, "N/A", true);
            } else {
                raw.addField(key, jobData.get(key), true);
            }
        }
        MessageEmbed msg = raw.build();
        TextChannel channel = Core.BOT.getTextChannelById(Core.ANALYTICS.get("jobs"));
        if (channel != null) {
            channel.sendMessageEmbeds(msg).queue();
        }

    }
}
