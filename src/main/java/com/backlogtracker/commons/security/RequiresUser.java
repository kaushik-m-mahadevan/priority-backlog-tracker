package com.backlogtracker.commons.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Any authenticated account (ADMIN or USER). The PENDING-account block is enforced
 * separately (ActiveAccountFilter); group-membership checks live in the services.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public @interface RequiresUser {
}
