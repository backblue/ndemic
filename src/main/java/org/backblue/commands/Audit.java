package org.backblue.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.backblue.core.Bot;
import org.backblue.config.Config;
import org.backblue.extension.Deployable;
import org.backblue.extension.LiveFramework;
import org.backblue.extension.SelfEditable;
import org.backblue.moderation.Auditing;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Audit extends ListenerAdapter implements
        LiveFramework.ButtonReturn,
        Deployable,
        SelfEditable {

    static Logger Log = LoggerFactory.getLogger(Audit.class);

    final @NotNull Bot bot;
    final @NotNull Auditing auditing;

    public Audit(@NonNull Bot bot, @NonNull Auditing auditing) {
        this.bot = bot;
        this.auditing = auditing;
    }

    @Override
    public Scope scope() {
        return Scope.whole(Config.Deployment_Audit_JSON);
    }

    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("audit") && event.getGuild() != null && event.getGuild().getId().equals(bot.getDeploymentGuild().getId())) {
            Container c = this.buildContainer();
            event.replyComponents(c).setEphemeral(true).useComponentsV2().queue(
                    hook -> hook.retrieveOriginal().queue(
                            message -> bot.getLiveContainer().applyContainerization(c, message, this)
                    )
            );
        }
    }

    @Override
    public Container onButton(@NonNull ButtonInteractionEvent event, String... actions) {
        org.backblue.enums.Audit action = Enum.valueOf(org.backblue.enums.Audit.class, actions[1]);
        boolean enable = !auditing.has(action);
        if (editable() && set("/" + action.configKey(), enable, event.getUser().getId())) {
            auditing.toggle(action);
        } else {
            Log.warn("Unable to save audit setting {}; left unchanged.", action);
        }
        return buildContainer();
    }

    private Container buildContainer() {
        List<ContainerChildComponent> settings = new ArrayList<>();
        settings.add(TextDisplay.of("## :clipboard: Audit Logging\n-# Toggle to listen to specific events."));
        for (org.backblue.enums.Audit action : org.backblue.enums.Audit.values()) {
            net.dv8tion.jda.api.components.buttons.Button button;
            if (auditing.has(action)) {
                button = Button.success(identifier()+";"+action.toString()+";on", "Enabled");
            } else {
                button = Button.danger(identifier()+";"+action.toString()+";off", "Disabled");
            }

            settings.add(Section.of(
                    button,
                    TextDisplay.of("**"+action.title()+"**")
            ));
        }
        return Container.of(settings);
    }

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("audit", "Enable/disable audit logging...")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)));
    }
}
