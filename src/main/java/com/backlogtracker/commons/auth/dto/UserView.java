package com.backlogtracker.commons.auth.dto;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.User;

/**
 * Safe user projection for API responses — never carries the password hash. Platform-wide
 * (commons) shape only; a per-applet preference like Backlog Tracker's grove animation
 * toggle does NOT belong here — see {@code GroveSettings} in the insights package, fetched
 * separately by whichever applet cares about it.
 *
 * <p>cln-9: this and {@link com.backlogtracker.commons.user.dto.UserSummary} are two
 * deliberately separate projections, not an accidental duplication — {@code UserView} is
 * "how I see myself" (login/register/`/auth/me`/profile-update responses), while
 * {@code UserSummary} is "how I see someone else" (member lists, pickers, the admin
 * roster). Both carry the same fields today; keep them in sync if a field is ever added
 * to one that the other's call sites would also need, but don't merge them into one type —
 * the two call-site shapes (self vs. others) are different enough that a future field
 * (e.g. something self-only like a notification preference) could easily belong on just
 * one side.
 */
public record UserView(String id, String name, String handle, String email, String role, String status) {

    public static UserView of(User user) {
        AccountStatus status = user.getStatus() == null ? AccountStatus.ACTIVE : user.getStatus();
        return new UserView(user.getId(), user.getName(), user.getHandle(), user.getEmail(),
                user.getRole().name(), status.name());
    }

    public static UserView of(AuthUser user) {
        return new UserView(user.id(), user.name(), user.handle(), user.email(),
                user.role().name(), user.status().name());
    }
}
