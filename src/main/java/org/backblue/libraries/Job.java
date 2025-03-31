package org.backblue.libraries;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.backblue.Core;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
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

    public Job(String name, String desc, String type) {
        this.name = name;
        this.desc = desc;
        this.type = type;
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
                return;
            } else if (job.processImage()) {
                TextChannel channel = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));
                Role pingRole = Core.BOT.getRoleById(Core.DEPLOYMENT.get("role.mod"));
                job.output += "Confidence level: 0";
                channel.sendMessage("Attention " + pingRole.getAsMention() + "\nAI thinks this picture is inappropriate: <@" + job.name + ">\n" + job.desc + job.output).queue();
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

    private Boolean processImage() {

        System.out.println("Processing failed");
        return null;
    }
}
