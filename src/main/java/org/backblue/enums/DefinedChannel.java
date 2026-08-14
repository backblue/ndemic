package org.backblue.enums;

public enum DefinedChannel {
    DebugDirectMessages("debugDirectMessages"),
    DebugAutoModAlert("debugAutoModAlert"),
    DebugEnforcement("debugEnforcement"),
    DebugImageDump("debugImageDump"),
    DeploymentBotCommands("deploymentBotCmds"),
    DeploymentWarnings("deploymentWarnings"),
    DeploymentLogs("deploymentLogs"),
    DeploymentHoney("deploymentHoney");

    private final String config;

    DefinedChannel(String config) {
        this.config = config;
    }

    public String configKey() {
        return config;
    }
}
