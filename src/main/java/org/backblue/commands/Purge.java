package org.backblue.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

public class Purge extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("purgehour") && event.isFromGuild()) {
            if (event.getOption("user") == null) {
                return;
            }
            User a = event.getOption("user").getAsUser();
            Member member = event.getGuild().getMember(a);
            if (member != null) {
                Bot.getBot().purgeMessages(member, 1);
                event.reply("Purged last hour, up to 100 messages of " + a.getName() + " from the server.").setEphemeral(true).queue();
            } else {
                event.reply("User not found in the server.").setEphemeral(true).queue();
            }
        }
    }
}
