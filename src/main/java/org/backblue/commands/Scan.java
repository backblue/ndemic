package org.backblue.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.utilities.FeatureFlag;
import org.backblue.wrappers.ProfileScanner;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

public class Scan extends ListenerAdapter {

    Bot bot;
    ProfileScanner scanner;

    public Scan(Bot bot, ProfileScanner scanner) {
        this.bot = bot;
        this.scanner = scanner;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("scan")) {
            if (bot.isFeatureEnabled(FeatureFlag.ScanProfiles)) {
                if ("profile".equals(event.getSubcommandName()) && event.getOption("user") != null) {
                    Member member = Objects.requireNonNull(event.getOption("user")).getAsMember();
                    if (member != null) event.reply("Scan initiated for " + member.getAsMention() + ". Please wait").setEphemeral(true).queue();
                    this.scanner.scan(member);
                }
                if ("link".equals(event.getSubcommandName())) {
                    event.deferReply().setEphemeral(true).queue();
                    String link = Objects.requireNonNull(event.getOption("link")).getAsString();
                    ProfileScanner.ScanResult result = this.scanner.scan("Manual Scan", link);
                    if (result != null) {
                        event.getHook().sendMessage("Profile Points: " + result.points()).setEphemeral(true).queue();
                    } else {
                        event.getHook().sendMessage("Invalid link").setEphemeral(true).queue();
                    }
                }
            } else {
                event.reply("Temporarily disabled").queue();
            }
        }
    }

}
