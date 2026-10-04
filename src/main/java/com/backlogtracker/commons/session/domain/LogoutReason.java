package com.backlogtracker.commons.session.domain;

public enum LogoutReason {
    /** The user pressed Sign out. */
    SIGN_OUT,
    /** No activity for the configured timeout (swept by the server, or reported by the
     *  browser's own idle timer and confirmed against the server's last-activity record). */
    INACTIVITY_TIMEOUT
}
