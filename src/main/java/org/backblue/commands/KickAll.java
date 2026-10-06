package org.backblue.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.backblue.core.Bot;
import org.backblue.extension.Deployable;
import org.backblue.extension.LiveFramework;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class KickAll extends ListenerAdapter implements LiveFramework.ButtonReturn, Deployable {

    final Bot bot;
    final Set<String> validRoleIDs = new HashSet<>();

    public KickAll(Bot bot, JSONObject json) {
        this.bot = bot;
        if (json != null && json.optJSONArray("susRoles") != null) {
            for (int i = 0; i < json.getJSONArray("susRoles").length(); i++) {
                Object obj = json.getJSONArray("susRoles").get(i);
                if (obj instanceof String s) {
                    validRoleIDs.add(s);
                } else if (obj instanceof JSONObject jsonObject) {
                    validRoleIDs.add(jsonObject.getString("id"));
                }
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("kickall") || event.getGuild() == null) return;

        Role roleToRemove = Objects.requireNonNull(event.getOption("role")).getAsRole();
        if (!validRoleIDs.contains(roleToRemove.getId())) {
            event.reply("Not configured to allow " + roleToRemove.getAsMention() + " role members to be removed.").setEphemeral(true).queue();
            return;
        }

        int count = event.getGuild().getMembersWithRoles(roleToRemove).size();
        Container c = buildConfirmContainer(roleToRemove, count);
        event.replyComponents(c).setEphemeral(true).useComponentsV2().queue(
                hook -> hook.retrieveOriginal().queue(
                        message -> bot.getLiveContainer().applyContainerization(c, message, this)
                )
        );
    }

    @Override
    public Container onButton(@NonNull ButtonInteractionEvent event, String... actions) {
        if (event.getGuild() == null || actions.length < 3 || !"confirm".equals(actions[1])) return null;

        Role role = event.getGuild().getRoleById(actions[2]);
        if (role == null || !validRoleIDs.contains(actions[2])) {
            return Container.of(TextDisplay.of("-# That role is no longer configured or valid."));
        }

        List<Member> targets = event.getGuild().getMembersWithRoles(role);
        int count = targets.size();
        String executor = Objects.requireNonNull(event.getMember()).getUser().getName();
        targets.forEach(member -> member.kick().reason("Kick-all initiated by " + executor).queue());

        return Container.of(
                TextDisplay.of("Kicked **" + count + " members** from " + role.getAsMention() + "."),
                ActionRow.of(Button.success(identifier() + ";confirm;" + role.getId(), "Confirm").asDisabled()),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# This cannot be undone.")
        );
    }

    private Container buildConfirmContainer(Role role, int count) {
        return Container.of(
                TextDisplay.of("This will kick **" + count + " members** in " + role.getAsMention() + ". Are you sure?"),
                ActionRow.of(Button.success(identifier() + ";confirm;" + role.getId(), "Confirm")),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# This cannot be undone.")
        );
    }

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("kickall", "Kicks all members from a defined, well-specified role.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addOption(OptionType.ROLE, "role", "Target role", true));
    }
}
