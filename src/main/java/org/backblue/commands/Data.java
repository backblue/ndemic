package org.backblue.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.Bot;
import org.backblue.utilities.SQLProfile;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.io.File;
import java.io.FileWriter;
import java.util.Objects;

public class Data extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("data")) {
            if (event.getSubcommandName() == null) {
                return;
            }
            if (event.getSubcommandName().equals("overwrite")) {
                Bot.getBot().sendTaskUpdate();
                event.reply(":white_check_mark: Forced writing of cache files early to disk!").setEphemeral(true).queue();
            }
            if (event.getSubcommandName().equals("all")) {
                event.deferReply(true).queue();
                String table = Objects.requireNonNull(event.getOption("table")).getAsString();
                JSONArray data = SQLProfile.readAllIntoArray(table);
                File file = new File("data-all-" + table + ".json");
                try (FileWriter writer = new FileWriter(file)){
                    writer.write(data != null ? data.toString() : null);
                } catch (Exception e) {
                    event.getHook().sendMessage(":x: Could not get data!").queue();
                    return;
                }
                event.getHook().sendFiles(FileUpload.fromData(file)).setContent("Requested data from table `" + table + "`").queue();
            }
            if (event.getSubcommandName().equals("user")) {
                event.deferReply(true).queue();
                String table = Objects.requireNonNull(event.getOption("table")).getAsString();
                User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
                if (user.isBot() || !SQLProfile.exists(user.getId(), table)) {
                    event.getHook().sendMessage(":x: User **" + user.getName() + "** has no entries in table `" + table + "`").setEphemeral(true).queue();
                    return;
                }
                String data = SQLProfile.read(user.getId(), table).toString();
                File file = new File("data-" + table + ".json");
                try (FileWriter writer = new FileWriter(file)){
                    writer.write(data);
                } catch (Exception e) {
                    event.getHook().sendMessage(":x: Could not get data!").queue();
                    return;
                }
                event.getHook().sendFiles(FileUpload.fromData(file)).setContent("Requested data for: **" + user.getName() + "** from table `" + table + "`").queue();
            }
        }
    }
}