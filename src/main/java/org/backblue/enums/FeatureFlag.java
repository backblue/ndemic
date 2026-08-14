package org.backblue.enums;

public enum FeatureFlag {
    EnforceOneGuideAccess("enforceGuideAccess", false),
    BlueSky("blueSky", true),
    Honeypot("honeypot", false),
    DisableDMs("disableDMs", false),
    AutoModAlerts("autoModAlerts", false),
    RoleIcons("roleIcons",  true),
    MessageForwarding("msgForward", true),
    RaidPauseInvites("raidPauseInvites", false),
    NitroBoostMessage("nitroBoostMessage", false),
    ScanProfiles("scanProfiles", true),
    AI("ai", true),
    DetectCrypto("detectCryptoImages", true),
    Gatekeeper("gatekeeper", true),
    Gatekeeper_RequireOnboarding("requireOnboarding", false),
    Gatekeeper_RemoveLowQualityAccounts("removeLowQualityAccounts", false),
    Autoresponder("autoresponder", false),
    Audit("audit", false);

    private final String config;
    private final boolean restrict;

    FeatureFlag(String config, boolean restrict) {
        this.config = config;
        this.restrict = restrict;
    }

    public String configKey() {
        return config;
    }
    public boolean restricted() {
        return restrict;
    }
}
