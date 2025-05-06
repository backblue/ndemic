package org.backblue.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.backblue.Core;
import org.backblue.utilities.SQLJSON;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.io.File;
import java.io.FileWriter;

public class Data extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("data")) {
            if (!Core.SETTINGS.getBoolean("useSQL")) {
                event.reply(":x: `/data` only has support for SQL-mode").setEphemeral(true).queue();
                return;
            }
            if (event.getSubcommandName().equals("all")) {
                event.deferReply(true).queue();
                String table = event.getOption("table").getAsString();
                JSONArray data = SQLJSON.readAllIntoArray(table);
                File file = new File("data-all-" + table + ".json");
                try (FileWriter writer = new FileWriter(file)){
                    writer.write(data.toString());
                } catch (Exception e) {
                    event.getHook().sendMessage(":x: Could not get data!").queue();
                    e.printStackTrace();
                    return;
                }
                event.getHook().sendFiles(FileUpload.fromData(file)).setContent("Requested data from table `" + table + "`").queue();
            }
            if (event.getSubcommandName().equals("user")) {
                event.deferReply(true).queue();
                String table = event.getOption("table").getAsString();
                User user = event.getOption("user").getAsUser();
                if (user.isBot() || !SQLJSON.exists(user.getId(), table)) {
                    event.getHook().sendMessage(":x: User **" + user.getName() + "** has no entries in table `" + table + "`").setEphemeral(true).queue();
                    return;
                }
                String data = SQLJSON.read(user.getId(), table).toString();
                File file = new File("data-" + table + ".json");
                try (FileWriter writer = new FileWriter(file)){
                    writer.write(data);
                } catch (Exception e) {
                    event.getHook().sendMessage(":x: Could not get data!").queue();
                    e.printStackTrace();
                    return;
                }
                event.getHook().sendFiles(FileUpload.fromData(file)).setContent("Requested data for: **" + user.getName() + "** from table `" + table + "`").queue();
            }
        }
    }
}
