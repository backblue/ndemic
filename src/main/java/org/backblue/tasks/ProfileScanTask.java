package org.backblue.tasks;

import net.dv8tion.jda.api.entities.User;
import org.backblue.Bot;

public class ProfileScanTask extends Task {

    private final User user;
    private String avatarLink;
    private String bannerLink;
    private final String source;

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void process() {

    }

    public ProfileScanTask(User user, String source) {
        super();
        this.user = user;
        this.avatarLink = user.getEffectiveAvatarUrl();
        user.retrieveProfile().queue(profile -> this.bannerLink = profile.getBannerUrl());
        this.source = source;
    }
}
