package org.backblue.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.backblue.core.Bot;
import org.backblue.enums.Deployable;
import org.backblue.enums.FeatureFlag;
import org.backblue.cloud.ProfileScan;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class Scan extends ListenerAdapter implements Deployable {

    Bot bot;
    ProfileScan scanner;

    public Scan(Bot bot, ProfileScan scanner) {
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
                    CompletableFuture.runAsync(()-> this.scanner.scan(member));

                }
                if ("link".equals(event.getSubcommandName())) {
                    event.deferReply().setEphemeral(true).queue();
                    String link = Objects.requireNonNull(event.getOption("link")).getAsString();
                    ProfileScan.ScanResult result = this.scanner.scan("Manual Scan", link);
                    if (result != null) {
                        event.getHook().sendMessage("Profile Points: " + result.points()).setEphemeral(true).queue();
                    } else {
                        event.getHook().sendMessage("Invalid link").setEphemeral(true).queue();
                    }
                }
            } else {
                event.reply("Temporarily disabled").setEphemeral(true).queue();
            }
        }
    }

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("scan", "Scan management")
                .addSubcommands(new SubcommandData("profile", "Manually initiate a profile scan for an user")
                        .addOption(OptionType.USER, "user", "Select a user", true))
                .addSubcommands(new SubcommandData("link", "Manually initiate a link scan")
                        .addOption(OptionType.STRING, "link", "Input valid link", true))
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
    }
}
