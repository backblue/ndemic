package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.core.Bot;
import org.backblue.enums.Deployable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.UncheckedIOException;
import java.util.List;

public class Privacy extends ListenerAdapter implements Deployable {

    Bot bot;

    public Privacy(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
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

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("privacy", "View the bot's privacy policy"));
    }
}
