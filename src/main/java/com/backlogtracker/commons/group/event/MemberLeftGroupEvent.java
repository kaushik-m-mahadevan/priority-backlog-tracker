package com.backlogtracker.commons.group.event;

/**
 * Published whenever a member leaves a group, so applet-specific state that depends on
 * "everyone currently in the group" (e.g. Order Tracker's unanimous cost-config approval)
 * can react without {@code commons} importing any applet package — the applet listens,
 * commons never knows who's listening (platform integration: dependency direction stays
 * one-way, commons → nothing, applets → commons).
 */
public record MemberLeftGroupEvent(String groupId, String userId) {
}
