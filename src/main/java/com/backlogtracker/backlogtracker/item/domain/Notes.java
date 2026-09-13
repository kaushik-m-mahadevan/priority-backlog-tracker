package com.backlogtracker.backlogtracker.item.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single embedded notes object per item (design §16/§17). No history. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notes {

    private String content;
    private String format;   // always "markdown" for now
    private Instant updatedAt;

    public static Notes markdown(String content) {
        return new Notes(content, "markdown", Instant.now());
    }
}
