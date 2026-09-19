package com.backlogtracker.commons.user.dto;

import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.User;

/** User projection for pickers, member lists and the admin roster. Never carries the hash.
 *
 *  <p>cln-9: see {@link com.backlogtracker.commons.auth.dto.UserView}'s javadoc for why this
 *  is a deliberately separate "how I see someone else" projection rather than a duplicate of
 *  that "how I see myself" one. */
public record UserSummary(String id, String name, String handle, String email,
                          String role, String status) {

    public static UserSummary of(User u) {
        AccountStatus status = u.getStatus() == null ? AccountStatus.ACTIVE : u.getStatus();
        return new UserSummary(u.getId(), u.getName(), u.getHandle(), u.getEmail(),
                u.getRole().name(), status.name());
    }
}
