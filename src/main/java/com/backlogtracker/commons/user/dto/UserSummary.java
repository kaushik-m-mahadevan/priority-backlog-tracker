package com.backlogtracker.commons.user.dto;

import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.User;

/** User projection for pickers, member lists and the admin roster. Never carries the hash. */
public record UserSummary(String id, String name, String handle, String email,
                          String role, String status) {

    public static UserSummary of(User u) {
        AccountStatus status = u.getStatus() == null ? AccountStatus.ACTIVE : u.getStatus();
        return new UserSummary(u.getId(), u.getName(), u.getHandle(), u.getEmail(),
                u.getRole().name(), status.name());
    }
}
