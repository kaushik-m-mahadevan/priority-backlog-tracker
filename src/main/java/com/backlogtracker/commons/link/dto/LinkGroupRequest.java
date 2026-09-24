package com.backlogtracker.commons.link.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code inviteAllMembers} is the Setup Wizard's "invite all outright" behaviour (mb-23) —
 *  omit it (defaults false) for the ordinary manual-link path, where the linker reviews a
 *  suggested delta and sends their own edited set of invites separately. */
public record LinkGroupRequest(@NotBlank String groupId, boolean inviteAllMembers) {
}
