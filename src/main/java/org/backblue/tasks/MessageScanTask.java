package org.backblue.tasks;

import net.dv8tion.jda.api.entities.User;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Objects;

public final class MessageScanTask extends Task {

    private @NotNull final User user;
    private @NotNull final String msg;

    public MessageScanTask(String user, @NotNull String msg) {
        super();
        this.user = Objects.requireNonNull(Bot.getBot().getJDA().getUserById(user));
        this.msg = msg;
    }

    @Override
    public void process() {

    }

    @Override
    public HashMap<String, String> lookup() {
        HashMap<String, String> map = lookupBase();
        map.put("msg", msg);
        map.put("userID", user.getId());
        map.put("user", user.getName());
        return map;
    }
}
