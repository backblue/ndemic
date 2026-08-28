package org.backblue.enums;

public enum AuditAction {

    MessageDelete("messageDelete", "Message Deletion"),
    MessageEdit("messageEdit", "Message Edit"),
    MemberJoin("memberJoin", "Member Join"),
    MemberLeave("memberLeave", "Member Leave"),
    MembersRoleRemove("memberRolesRemove", "Member Role Remove"),
    MembersRoleAdd("memberRolesAdd", "Member Role Add"),
    MembersBan("memberBan", "Member Ban"),
    MembersUnban("memberUnban", "Member Unban");

    private final String config;
    private final String title;

    AuditAction(String config, String title) {
        this.config = config;
        this.title = title;
    }

    public String configKey() {
        return config;
    }
    public String title() {
        return title;
    }
}
