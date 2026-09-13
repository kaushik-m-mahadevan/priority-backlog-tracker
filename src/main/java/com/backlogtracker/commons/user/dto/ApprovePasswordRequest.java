package com.backlogtracker.commons.user.dto;

/** Body for approving a password request. {@code temporaryPassword} is required only
 *  for RESET requests (the admin sets one to hand over); ignored for CHANGE. */
public record ApprovePasswordRequest(String temporaryPassword) {
}
