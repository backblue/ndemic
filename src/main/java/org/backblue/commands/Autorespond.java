package org.backblue.commands;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.backblue.core.Bot;
import org.backblue.enums.LiveFramework;
import org.backblue.moderation.Autoresponding;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Autorespond extends ListenerAdapter implements LiveFramework.ButtonVoid, LiveFramework.Pagination {

    final static int ELEMENTS_PER_PAGE = 8;
    final Bot bot;
    final Map<Long, ContainerElement> m = new ConcurrentHashMap<>();
    final Autoresponding autoresponding;

    public Autorespond(Bot bot, Autoresponding autoresponding) {
        this.bot = bot;
        this.autoresponding = autoresponding;
    }

    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("autorespond") && event.getGuild() != null && event.getGuild().getId().equals(bot.getDeploymentGuild().getId())) {
            Container c = this.buildContainer(1);
            event.replyComponents(c).setEphemeral(true).useComponentsV2().queue(
                    hook -> hook.retrieveOriginal().queue(
                            message -> {
                                bot.getLiveContainer().applyContainerization(c, message, this);
                                m.put(message.getIdLong(), new ContainerElement(1));
                            }
                    )
            );
        }
    }

    @Override
    public void onButton(@NonNull ButtonInteractionEvent event, String... actions) {
        String action = actions[1];
        if (action.equals("create")) {
            TextInput keyword = TextInput.create("autorespond;input", TextInputStyle.SHORT)
                    .setPlaceholder("Enter text here...")
                    .setMinLength(1)
                    .setMaxLength(32)
                    .build();
            TextInput response = TextInput.create("autorespond;response", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Enter text here...")
                    .setMinLength(1)
                    .setMaxLength(4000)
                    .build();
            RadioGroup selection = RadioGroup.create("autorespond;matching")
                    .addOption("Exact", "autorespond;matching:exact")
                    .addOption("Contains", "autorespond;matching:contain")
                    .build();

            Modal modal = Modal.create("modal;autorespondCreate", "New Autoresponder Trigger")
                    .addComponents(
                            Label.of("Keyword", keyword),
                            Label.of("Message Matching", selection),
                            Label.of("Response", response)
                    )
                    .build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {
        if ("modal;autorespondCreate".equals(event.getModalId())) {
            event.deferReply(true).queue();

            event.getHook().editOriginal("Rule **[rule]** is active! You may need to refresh the container before you can see/edit it.").queue();
        }
    }

    @Override
    public Container onButtonNext(long messageId) {
        if (m.containsKey(messageId)) {
            ContainerElement e = m.get(messageId);
            e.page++;
            return buildContainer(e.page);
        }
        return null;
    }

    @Override
    public Container onButtonPrevious(long messageId) {
        if (m.containsKey(messageId)) {
            ContainerElement e = m.get(messageId);
            e.page--;
            return buildContainer(e.page);
        }
        return null;
    }

    private Container buildContainer(int page) {
        List<ContainerChildComponent> settings = new ArrayList<>();

        if (autoresponding.getEmojis().isEmpty() && autoresponding.getMessages().isEmpty()) {
            settings.add(Section.of(
                    Button.success(identifier()+";create", "Create..."),
                    TextDisplay.of("## :robot: Autoresponder\n-# There are no rules created yet.")
            ));
            return Container.of(settings);
        }
        int total = autoresponding.getMessages().size() + autoresponding.getEmojis().size();
        settings.add(Section.of(
                Button.success(identifier()+";create", "Create..."),
                TextDisplay.of(String.format("## :robot: Autoresponder - %s total\n-# Automatically respond to text triggers.", total))
        ));

        int start = (page - 1) * ELEMENTS_PER_PAGE;
        int end = Math.min(start + ELEMENTS_PER_PAGE, total);
        for (int i = start; i < end; i++) {
            Autoresponding.AutoresponderEntry n = autoresponding.getLibrary().get(i);
            if (n instanceof Autoresponding.AutoresponderMessage(String keyword, String response, boolean exact)) {
                String cpy = response.replace("\n", "").replace("*", "");
                if (cpy.length() > 50) cpy = cpy.substring(0, 50) + " *`...`*";
                String quoteOrStar = exact ? "\"" : "\\*";
                settings.add(Section.of(
                        Button.secondary(identifier()+";genericOption"+i, Emoji.fromUnicode("U+1F4DD")).asDisabled(),
                        TextDisplay.of(String.format("**%s%s%s**\n%s", quoteOrStar, keyword, quoteOrStar, cpy))
                ));
            } else if (n instanceof Autoresponding.AutoresponderEmoji(String keyword, String emoji, boolean exact)) {
                String quoteOrStar = exact ? "\"" : "\\*";
                settings.add(Section.of(
                        Button.secondary(identifier()+";genericOption"+i, Emoji.fromUnicode("U+1F4DD")).asDisabled(),
                        TextDisplay.of(String.format("**%s%s%s**\n*Emoji Reaction:* %s", quoteOrStar, keyword, quoteOrStar, emoji))
                        ));
            }
        }

        settings.add(Separator.create(true, Separator.Spacing.SMALL));
        settings.add(buildPagingRows(page, ELEMENTS_PER_PAGE, total));
        return Container.of(settings);
    }

    static class ContainerElement {
        int page;
        public ContainerElement(int page) {
            this.page = page;
        }
    }
}