package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.backblue.core.Bot;
import org.backblue.enums.Deployable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;

public class About extends ListenerAdapter implements Deployable {

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
                    .setTitle(event.getJDA().getSelfUser().getName() + ": v" + bot.major + "." + bot.minor + "." + bot.patch)
                    .addField("Uptime", "`" + bot.formattedTime(OffsetDateTime.now().toEpochSecond() - bot.createdSince, true) + "`", false);
            if (!watermark.isEmpty()) {
                embed.setFooter(this.watermark);
            }
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("about", "Uptime & bot information"));
    }
}
