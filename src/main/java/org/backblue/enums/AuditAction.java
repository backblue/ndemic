package org.backblue.enums;

public enum AuditAction {

    MessageDelete("messageDelete"),
    MessageEdit("messageEdit"),
    MemberJoin("memberJoin"),
    MemberLeave("memberLeave"),
    MembersRoleRemove("memberRolesRemove"),
    MembersRoleAdd("memberRolesAdd"),
    MembersBan("memberBan"),
    MembersUnban("memberUnban");

    private final String config;

    AuditAction(String config) {
        this.config = config;
    }

    public String configKey() {
        return config;
    }
}
