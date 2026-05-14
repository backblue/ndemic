package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

public class Security extends ListenerAdapter {

    private static final String on = ":white_check_mark:";
    private static final String off = "no_entry_sign:";
    
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("security") && event.getSubcommandName() != null) {
            if (event.getSubcommandName().equals("info")) {

                String str = "Enforce Onboarding Completion: " + (Bot.getBot().getSentinelManager().enforceOnboarding ? on : off) + "\n" +
                        "Random Checks: " + (Bot.getBot().getSentinelManager().randomChecks ? on : off) + "\n" +
                        "Gatekeeper: " + (Bot.getBot().getSentinelManager().gatekeeper ? on : off) + "\n";

                event.reply((str)).setEphemeral(true).queue();
            }
        }
    }
}