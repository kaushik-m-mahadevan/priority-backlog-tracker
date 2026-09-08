package com.backlogtracker.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/** Owners and Contributors may mutate shared items; Viewers are read-only (design §8). */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('OWNER','CONTRIBUTOR')")
public @interface RequiresContributor {
}
