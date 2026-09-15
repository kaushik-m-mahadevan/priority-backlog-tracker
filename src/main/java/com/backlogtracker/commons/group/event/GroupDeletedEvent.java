package com.backlogtracker.commons.group.event;

/**
 * Published when a group is deleted (its last member left). Lets applet-specific or
 * cross-applet state that references a groupId (e.g. a {@code GroupLink}) clean itself up
 * without {@code commons} depending on who's listening — same one-way dependency
 * direction as {@link MemberLeftGroupEvent}.
 */
public record GroupDeletedEvent(String groupId) {
}
