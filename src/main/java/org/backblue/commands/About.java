package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.UncheckedIOException;

public class About extends ListenerAdapter {

    Bot bot;
    String watermark;

    public About(Bot bot, String watermark) {
        this.bot = bot;
        this.watermark = watermark;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("about")) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setTitle(event.getJDA().getSelfUser().getName() + ": v" + bot.major + "." + bot.minor + "." + bot.patch);
            if (!watermark.isEmpty()) {
                embed.setFooter(this.watermark);
            }
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
        if (event.getName().equals("privacy")) {
            event.deferReply(true).queue();
            try {
                FileUpload upload = FileUpload.fromData(new File("data/privacypolicy.txt"));
                event.getHook().sendFiles(upload).queue();
            } catch (UncheckedIOException e) {
                event.getHook().sendMessage("No privacy policy has been set for " + event.getJDA().getSelfUser().getAsMention() + ".").queue();
            }

        }
    }

}
