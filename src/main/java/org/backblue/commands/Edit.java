package org.backblue.commands;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.backblue.core.Bot;
import org.backblue.edit.EditBadges;
import org.backblue.edit.EditRulebook;
import org.backblue.edit.EditSettings;
import org.backblue.extension.Deployable;
import org.backblue.extension.LiveFramework;
import org.backblue.extension.SelfEditable;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class Edit extends ListenerAdapter implements
        Deployable,
        LiveFramework.Pagination,
        LiveFramework.ButtonReturn{

    final Bot bot;
    final EditRulebook editRulebook;
    final EditSettings editSettings;
    final EditBadges editBadges;
    public Edit(Bot bot) {
        this.bot = bot;
        editRulebook = new EditRulebook();
        editSettings = new EditSettings();
        editBadges = new EditBadges();
    }

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("edit", "Change configurations of non-sensitive information")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                .addSubcommands(new SubcommandData("badges", "Change badges configuration"))
                .addSubcommands(new SubcommandData("rulebook", "Change rulebook configuration"))
                .addSubcommands(new SubcommandData("bluesky", "Change bluesky tracking"))
                .addSubcommands(new SubcommandData("forwarding", "Change forwarding channels"))
                .addSubcommands(new SubcommandData("crypto", "Change crypto detection rules"))
                .addSubcommands(new SubcommandData("profile", "Change profile scanning rules"))
                .addSubcommands(new SubcommandData("self", "Change bot personalization"))
        );
    }

    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {
        if ("edit".equals(event.getName()) && event.getSubcommandName() != null) {
            if (event.getSubcommandName() == null) {
                event.reply("Invalid editor").queue();
                return;
            }
            switch (event.getSubcommandName()) {
                case "badges":
                    event.replyComponents(editBadges.badges()).queue(
                            hook -> hook.retrieveOriginal().queue(msg -> bot.getLiveContainer().applyContainerization(editBadges.badges(), msg, editBadges))
                    );
                    break;
                case "rulebook":
                    event.reply("r subcmd").queue();
                    break;
                case "bluesky":
                    event.reply("bs subcmd").queue();
                    break;
                case "forwarding":
                    event.reply("f subcmd").queue();
                    break;
                case "crypto":
                    event.reply("c subcmd").queue();
                    break;
                case "profile":
                    event.reply("p subcmd").queue();
                    break;
                case "self":
                    event.reply("sf subcmd").queue();
                    break;
            }
        }
    }

    @Override
    public Container onButton(@NonNull ButtonInteractionEvent event, String... actions) {
        return null;
    }

    @Override
    public Container onButtonNext(long messageId) {
        return null;
    }

    @Override
    public Container onButtonPrevious(long messageId) {
        return null;
    }
}
