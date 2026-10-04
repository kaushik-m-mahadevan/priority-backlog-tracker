package com.backlogtracker.commons.session.dto;

/** The inactivity timeout, in minutes: shown/edited in the admin console and read by the
 *  browser so its idle timer matches the server's. */
public record SessionPolicyView(int timeoutMinutes) {
}
