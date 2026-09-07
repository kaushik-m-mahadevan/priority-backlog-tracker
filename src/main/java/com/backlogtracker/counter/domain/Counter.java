package com.backlogtracker.counter.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Atomic sequence counter (design §20). One document per counter key, e.g.
 * {@code "shared"} or {@code "personal-TST"}.
 */
@Document("counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Counter {

    @Id
    private String id;

    private long seq;
}
