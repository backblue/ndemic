package org.backblue.commands;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
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
import net.dv8tion.jda.api.entities.Message;
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
import java.util.concurrent.CompletableFuture;
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
        if (event.getName().equals("autorespond") && event.getGuild() != null) {
            Container c = this.buildContainer(1);
            event.replyComponents(c).setEphemeral(true).useComponentsV2().queue(
                    hook -> hook.retrieveOriginal().queue(
                            message -> {
                                bot.getLiveContainer().applyContainerization(c, message, this);
                                m.put(message.getIdLong(), new ContainerElement());
                            }
                    )
            );
        }
    }

    @Override
    public void onButton(@NonNull ButtonInteractionEvent event, String... actions) {
        String action = actions[1];
        switch (action) {
            case "create" -> {
                RadioGroup textOrEmoji = RadioGroup.create("autorespond;textOrEmoji")
                        .addOption("String Message Response", "autorespond;textOrEmoji;string")
                        .addOption("Add Reaction Emoji", "autorespond;textOrEmoji;emoji")
                        .build();
                TextInput keyword = TextInput.create("autorespond;input", TextInputStyle.SHORT)
                        .setPlaceholder("Enter text here...")
                        .setMinLength(1)
                        .setMaxLength(32)
                        .build();
                TextInput response = TextInput.create("autorespond;response", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Enter text here for message responses, or one EMOJI CODE for adding reaction emoji.")
                        .setMinLength(1)
                        .setMaxLength(4000)
                        .build();
                RadioGroup selection = RadioGroup.create("autorespond;matching")
                        .addOption("Exact", "autorespond;matching:exact")
                        .addOption("Contains", "autorespond;matching:contain")
                        .build();

                Modal modal = Modal.create("modal;autorespondCreate", "New Autoresponder Trigger")
                        .addComponents(
                                Label.of("Text or Emoji", textOrEmoji),
                                Label.of("Keyword", keyword),
                                Label.of("Message Matching", selection),
                                Label.of("Response", response)
                        )
                        .build();
                event.replyModal(modal).queue();
            }
            case "edit" -> {
                int index = Integer.parseInt(actions[2]);
                ContainerElement e = m.get(event.getMessageIdLong());
                if (e != null) {
                    e.editingIndex = index;
                }
                event.editComponents(buildEditContainer(index)).useComponentsV2().queue();
            }
            case "back" -> returnToPages(event);
            case "toggleMatching" -> {
                int index = Integer.parseInt(actions[2]);
                Autoresponding.AutoresponderEntry n = autoresponding.getAt(index);
                Autoresponding.AutoresponderEntry updated = getUpdated(n);

                autoresponding.updateAt(index, updated);
                event.editComponents(buildEditContainer(index)).useComponentsV2().queue();
                CompletableFuture.runAsync(this.autoresponding::writeToJSON);
            }
            case "delete" -> {
                int index = Integer.parseInt(actions[2]);
                autoresponding.deleteAt(index);
                CompletableFuture.runAsync(this.autoresponding::writeToJSON);
                returnToPages(event);
            }
            case "changeKeyword", "changeResponse" -> {
                int index = Integer.parseInt(actions[2]);
                Autoresponding.AutoresponderEntry n = autoresponding.getAt(index);

                String currentKeyword;
                String currentResponse;
                if (n instanceof Autoresponding.AutoresponderMessage j) {
                    currentKeyword = j.keyword();
                    currentResponse = j.response();
                } else {
                    Autoresponding.AutoresponderEmoji e = (Autoresponding.AutoresponderEmoji) n;
                    currentKeyword = e.keyword();
                    currentResponse = e.emoji();
                }

                String fieldChanged = action.equals("changeKeyword") ? "Keyword" : "Response";
                int keywordMaxLength = Math.max(32, currentKeyword.length());
                TextInput keywordInput = TextInput.create("autorespond;editKeyword", TextInputStyle.SHORT)
                        .setPlaceholder("Enter text here...")
                        .setMinLength(1)
                        .setMaxLength(keywordMaxLength)
                        .setValue(currentKeyword)
                        .build();
                TextInput responseInput = TextInput.create("autorespond;editResponse", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Enter text here for message responses, or one EMOJI CODE for reaction emoji.")
                        .setMinLength(1)
                        .setMaxLength(4000)
                        .setValue(currentResponse)
                        .build();

                Modal modal = Modal.create("modal;autorespondEdit;" + index, "Change " + fieldChanged)
                        .addComponents(
                                Label.of("Keyword", keywordInput),
                                Label.of("Response", responseInput)
                        )
                        .build();
                event.replyModal(modal).queue();
            }
        }

    }

    private static Autoresponding.@NonNull AutoresponderEntry getUpdated(Autoresponding.AutoresponderEntry n) {
        Autoresponding.AutoresponderEntry updated;

        if (n instanceof Autoresponding.AutoresponderMessage(String kw, String response, boolean isExact)) {
            updated = new Autoresponding.AutoresponderMessage(kw, response, !isExact);
        } else {
            Autoresponding.AutoresponderEmoji e = (Autoresponding.AutoresponderEmoji) n;
            updated = new Autoresponding.AutoresponderEmoji(e.keyword(), e.emoji(), !e.exact());
        }
        return updated;
    }

    private void returnToPages(@NonNull ButtonInteractionEvent event) {
        ContainerElement e = m.get(event.getMessageIdLong());
        int page = e != null ? e.page : 1;
        if (e != null) {
            e.editingIndex = -1;
        }
        event.editComponents(buildContainer(page)).useComponentsV2().queue();
    }

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {
        if ("modal;autorespondCreate".equals(event.getModalId())) {
            String textOrEmoji = event.getValues().get(0).getAsString();
            String keyword = event.getValues().get(1).getAsString().trim();
            String matching = event.getValues().get(2).getAsString();
            String response = event.getValues().get(3).getAsString().trim();

            if (autoresponding.contains(keyword)) {
                event.reply("Keyword " + keyword + " already is defined.").setEphemeral(true).queue();
                return;
            }

            Autoresponding.AutoresponderEntry entry;
            if (textOrEmoji.equals("autorespond;textOrEmoji;string")) {
                entry = new Autoresponding.AutoresponderMessage(keyword, response, matching.equals("autorespond;matching:exact"));
            } else {
                try {
                    Emoji.fromFormatted(response);
                } catch (IllegalArgumentException e) {
                    event.reply("Unable to parse emoji " + response + ".").setEphemeral(true).queue();
                    return;
                }
                entry = new Autoresponding.AutoresponderEmoji(keyword, response, matching.equals("autorespond;matching:exact"));
            }

            this.autoresponding.insert(entry);
            boolean saved = this.autoresponding.writeToJSON();

            event.deferEdit().queue();
            Message source = event.getMessage();
            if (source != null) {
                ContainerElement e = m.get(source.getIdLong());
                int page = e != null ? e.page : 1;
                event.getHook().editOriginalComponents(buildContainer(page)).useComponentsV2().queue();
            }
            if (!saved) {
                event.getHook().sendMessage("Rule **" + keyword + "** created but failed to save to disk.").setEphemeral(true).queue();
            }
        } else if (event.getModalId().startsWith("modal;autorespondEdit;")) {
            int index = Integer.parseInt(event.getModalId().split(";")[2]);
            String newKeyword = event.getValues().get(0).getAsString().trim();
            String newResponse = event.getValues().get(1).getAsString().trim();

            Autoresponding.AutoresponderEntry existing = autoresponding.getAt(index);
            String oldKeyword = existing instanceof Autoresponding.AutoresponderMessage msg
                    ? msg.keyword()
                    : ((Autoresponding.AutoresponderEmoji) existing).keyword();

            if (!newKeyword.equalsIgnoreCase(oldKeyword) && autoresponding.contains(newKeyword)) {
                event.reply("Keyword " + newKeyword + " already is defined.").setEphemeral(true).queue();
                return;
            }

            Autoresponding.AutoresponderEntry updated;
            if (existing instanceof Autoresponding.AutoresponderMessage k) {
                updated = new Autoresponding.AutoresponderMessage(newKeyword, newResponse, k.exact());
            } else {
                try {
                    Emoji.fromFormatted(newResponse);
                } catch (IllegalArgumentException e) {
                    event.reply("Unable to parse emoji " + newResponse + ".").setEphemeral(true).queue();
                    return;
                }
                boolean isExact = ((Autoresponding.AutoresponderEmoji) existing).exact();
                updated = new Autoresponding.AutoresponderEmoji(newKeyword, newResponse, isExact);
            }

            autoresponding.updateAt(index, updated);
            boolean saved = autoresponding.writeToJSON();

            event.deferEdit().queue();
            event.getHook().editOriginalComponents(buildEditContainer(index)).useComponentsV2().queue();
            if (!saved) {
                event.getHook().sendMessage("Rule **" + newKeyword + "** updated but failed to save to disk.").setEphemeral(true).queue();
            }
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
                        Button.secondary(identifier()+";edit;"+i, Emoji.fromUnicode("U+1F4DD")),
                        TextDisplay.of(String.format("**%s%s%s**\n%s", quoteOrStar, keyword, quoteOrStar, cpy))
                ));
            } else if (n instanceof Autoresponding.AutoresponderEmoji(String keyword, String emoji, boolean exact)) {
                String quoteOrStar = exact ? "\"" : "\\*";
                settings.add(Section.of(
                        Button.secondary(identifier()+";edit;"+i, Emoji.fromUnicode("U+1F4DD")),
                        TextDisplay.of(String.format("**%s%s%s**\n*Emoji Reaction:* %s", quoteOrStar, keyword, quoteOrStar, emoji))
                        ));
            }
        }

        settings.add(Separator.create(true, Separator.Spacing.SMALL));
        settings.add(buildPagingRows(page, ELEMENTS_PER_PAGE, total));
        return Container.of(settings);
    }

    private Container buildEditContainer(int index) {
        List<ContainerChildComponent> settings = new ArrayList<>();
        Autoresponding.AutoresponderEntry n = autoresponding.getLibrary().get(index);

        String keyword;
        boolean exact;
        String typeLabel;
        String responseValue;

        if (n instanceof Autoresponding.AutoresponderMessage(String kw, String response, boolean isExact)) {
            keyword = kw;
            exact = isExact;
            typeLabel = "Message";
            responseValue = response;
        } else if (n instanceof Autoresponding.AutoresponderEmoji(String kw, String emoji, boolean isExact)) {
            keyword = kw;
            exact = isExact;
            typeLabel = "Emoji";
            responseValue = emoji;
        } else {
            return buildContainer(1);
        }

        String quoteOrStar = exact ? "\"" : "\\*";

        settings.add(Section.of(
                Button.primary(identifier()+";back", "Back"),
                TextDisplay.of(String.format("## Edit %s%s%s", quoteOrStar, keyword, quoteOrStar))
        ));
        settings.add(TextDisplay.of(String.format("**Response -- %s**\n%s", typeLabel, responseValue)));
        settings.add(Separator.create(true, Separator.Spacing.SMALL));
        settings.add(ActionRow.of(
                Button.secondary(identifier()+";changeKeyword;"+index, "Change..."),
                Button.secondary(identifier()+";toggleMatching;"+index, "Toggle Matching"),
                Button.danger(identifier()+";delete;"+index, "Delete")
        ));

        return Container.of(settings);
    }

    static class ContainerElement {
        int page = 1;
        int editingIndex = -1;
    }
}