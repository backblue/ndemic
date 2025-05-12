package org.backblue.utilities;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import org.backblue.Core;

public class TakeAction {

    public static void kickWarnLog(Member user, String reason) {
        TextChannel guildWarn = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.warn"));
        user.kick().reason(reason).queue();
        StringBuilder msg = new StringBuilder();
        msg.append(user.getAsMention()).append("- kick - ").append(reason);
        if (guildWarn != null) {
            guildWarn.sendMessage(msg).queue();
        }
    }

    public static void kickWarnLog(Member user, String reason, AutoModExecutionEvent event) {
        TextChannel guildWarn = Core.BOT.getTextChannelById(Core.DEPLOYMENT.get("channel.warn"));
        user.kick().reason(reason).queue();
        StringBuilder link = new StringBuilder();
        link.append("https://discord.com/channels/").append(event.getGuild().getId()).append("/").append(event.getChannel().getId()).append("/").append(event.getAlertMessageId());
        StringBuilder msg = new StringBuilder();
        msg.append(user.getAsMention()).append("- kick - ").append(reason).append("\n").append(link);
        if (guildWarn != null) {
            guildWarn.sendMessage(msg).queue();
        }
    }

    private TakeAction(){}
}
