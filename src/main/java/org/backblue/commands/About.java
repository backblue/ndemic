package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.backblue.core.Bot;
import org.backblue.extension.Deployable;
import org.backblue.utilities.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.StringReader;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;

public class About extends ListenerAdapter implements Deployable {

    private static final @Nullable Instant BUILD_TIME = readBuildTime();
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
                    .addField("Uptime", "`" + Util.formattedTime(OffsetDateTime.now().toEpochSecond() - bot.createdSince, true) + "`", false);
            if (BUILD_TIME != null) {
                embed.addField("Built", "<t:" + BUILD_TIME.getEpochSecond() + ":f>", false);
            }
            if (!watermark.isEmpty()) {
                embed.setFooter(this.watermark);
            }
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }

    private static @Nullable Instant readBuildTime() {
        try {
            URL location = About.class.getProtectionDomain().getCodeSource().getLocation();
            if (!location.getPath().endsWith(".jar")) return null;
            Properties properties = new Properties();
            properties.load(new StringReader(Util.readResource("build.properties")));
            return Instant.parse(properties.getProperty("build.time", ""));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("about", "Uptime & bot information"));
    }
}
