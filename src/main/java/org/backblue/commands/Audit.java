package org.backblue.commands;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.enums.AuditAction;
import org.backblue.enums.LiveFramework;
import org.backblue.moderation.Auditing;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Audit extends ListenerAdapter implements LiveFramework.ButtonReturn {

    static Logger Log = LoggerFactory.getLogger(Audit.class);

    final @NotNull Bot bot;
    final @NotNull Auditing auditing;

    public Audit(@NonNull Bot bot, @NonNull Auditing auditing) {
        this.bot = bot;
        this.auditing = auditing;
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
        auditing.toggle(Enum.valueOf(AuditAction.class, actions[1]));
        CompletableFuture.runAsync(this::writeToFile);
        return buildContainer();
    }

    private void writeToFile() {
        synchronized (this) {
            JSONObject json = new JSONObject();
            EnumSet.allOf(AuditAction.class).forEach(action -> {
                json.put(action.configKey(), auditing.has(action));
            });
            json.put("_version", 1);
            json.put("webhookLink", auditing.webhookURL());
            File file = new File("data/deployment-audit.json");
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(json.toString(4));
            } catch (IOException e) {
                Log.warn("Unable to update file {}. Changes made this seesion will be lost on restart.", file);
            }
        }
    }

    private Container buildContainer() {
        List<ContainerChildComponent> settings = new ArrayList<>();
        settings.add(TextDisplay.of("## :clipboard: Audit Logging\n-# Toggle to listen to specific events."));
        for (AuditAction action : AuditAction.values()) {
            net.dv8tion.jda.api.components.buttons.Button button;
            if (auditing.has(action)) {
                button = Button.success(identifier()+";"+action.toString()+";on", "Enabled");
            } else {
                button = Button.danger(identifier()+";"+action.toString()+";off", "Disabled");
            }

            settings.add(Section.of(
                    button,
                    TextDisplay.of("**"+action.name()+"**")
            ));
        }
        return Container.of(settings);
    }
}
