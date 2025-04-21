package org.backblue.libraries;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public abstract class Job {

    private static int counter = 0;
    public final static Queue<Job> QUEUE = new LinkedList<>();
    public final static Stack<Job> RECENT_COMPLETE_JOBS = new Stack<>();
    public final static HashMap<Integer, Job> ID_TO_JOB = new HashMap<>();
    private String output = "\n";
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

    public final long getCompleted() {
        return completed;
    }

    protected Job() {
        this.id = counter++;
        ID_TO_JOB.put(this.id, this);
    }

    public final Long markInvalid() {
        this.output = this.output + ":exclamation:";
        return completed = System.currentTimeMillis() / 1000;
    }

    public static int getCounter() {
        return counter;
    }

    public final Long markDone() {
        this.output = this.output + " OK: <t:" + System.currentTimeMillis() / 1000 + ":R>";
        return completed = System.currentTimeMillis() / 1000;

    }

    public final Long markInvalid(String reason) {
        this.output = this.output + ":exclamation: " + reason;
        return completed = System.currentTimeMillis() / 1000;
    }

    public final Long markDone(String reason) {
        this.output = this.output + " OK: <t:" + System.currentTimeMillis() / 1000 + ":R> " + reason;
        return completed = System.currentTimeMillis() / 1000;
    }

    public final Long markDoneWithPrejudice(String reason) {
        this.output = this.output + " :warning: <t:" + System.currentTimeMillis() / 1000 + ":R> " + reason;
        return completed = System.currentTimeMillis() / 1000;
    }

    public final void ignore() {
        QUEUE.remove(this);
    }

    /*
     * Thanks to StackOverFlow for this method to download a URL (img) and convert it as a byte array.
     */
    protected byte[] downloadUrl(URL toDownload) {
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

    public abstract void process();
    public abstract HashMap<String, String> lookup();

    @Override
    public abstract String toString();
}
