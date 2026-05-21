package org.backblue.utilities;

public enum FeatureFlag {
    EnforceOneGuideAccess("enforceGuideAccess", false),
    BlueSky("blueSky", true),
    Honeypot("honeypot", false),
    DisableDMs("disableDMs", false),
    AutoModAlerts("autoModAlerts", false),
    RoleIcons("roleIcons",  true),
    MessageForwarding("msgForward", true),
    RaidPauseInvites("raidPauseInvites", false),
    ScanProfiles("scanProfiles", true);

    private final String config;
    private final boolean restrict;

    FeatureFlag(String config, boolean restrict) {
        this.config = config;
        this.restrict = restrict;
    }

    public String getConfigKey() {
        return config;
    }
    public boolean isRestricted() {
        return restrict;
    }
}
