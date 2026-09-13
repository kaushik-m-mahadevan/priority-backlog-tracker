package com.backlogtracker.backlogtracker.insights.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's opt-in for the grove animation (design: "the grove suffers"). Deliberately not
 * a field on {@link com.backlogtracker.commons.user.domain.User} — that document is a
 * platform-wide "commons" concept shared by every applet, and whether one particular
 * applet's tree wilts on screen has nothing to do with the account itself. One document
 * per user, keyed by their user id, defaulting to {@code false} when absent (the common
 * case: create-on-write, no upfront seeding needed).
 */
@Document("groveSettings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroveSettings {

    @Id
    private String userId;

    private boolean animationsEnabled;
}
