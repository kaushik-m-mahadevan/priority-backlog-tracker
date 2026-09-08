package com.backlogtracker.config.domain;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only audit of every Settings change (design §15). */
@Document("configHistory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigHistory {

    @Id
    private String id;

    private String changedBy;
    private Instant timestamp;
    private String summary;
    private Map<String, Object> previousValues;
    private Map<String, Object> newValues;
}
