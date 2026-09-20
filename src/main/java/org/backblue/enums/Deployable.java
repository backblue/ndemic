package org.backblue.enums;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

public interface Deployable {

    List<CommandData> cmds();

}
