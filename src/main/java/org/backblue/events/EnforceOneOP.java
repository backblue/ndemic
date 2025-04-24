package org.backblue.events;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class EnforceOneOP extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (Core.MODULES.get("enforceOPGuides")) {
            if (event.getChannelType().equals(ChannelType.GUILD_PUBLIC_THREAD)) {
                ThreadChannel thread = (ThreadChannel) event.getChannel();
                if (thread.getParentChannel().getName().contains("guides")) {
                    if (!Objects.equals(thread.getOwner(), event.getMessage().getMember())) {
                        try {
                            event.getMessage().delete().queue();
                        } catch (Exception ignored) {}
                        Objects.requireNonNull(event.getMember()).getUser().openPrivateChannel()
                                .queue(channel -> channel.sendMessage(
                                                "Hello,\n\nYou can not send messages in guides that aren't yours.\n\nFeel free to discuss the guide in the respective game channel (eg. `#rebel-inc` for Rebel Inc.)"
                                        ).queue(),
                                        error -> {});
                        if (Core.MODULES.get("analytics")) {
                            TextChannel channel = event.getJDA().getTextChannelById(Core.ANALYTICS.get("enforcement"));
                            Objects.requireNonNull(channel).sendMessage("Prevented user " + event.getMember().getAsMention() + " from chatting in <#" + thread.getId() + "> due to not being OP.").queue();
                        }
                    }
                }
            }
        }
    }
}
