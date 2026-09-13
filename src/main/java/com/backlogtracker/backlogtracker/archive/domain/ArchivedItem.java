package com.backlogtracker.backlogtracker.archive.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.backlogtracker.backlogtracker.item.domain.EffortEstimate;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.Notes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A completed item (design §24). Same shape as {@link Item} plus the terminal fields.
 * Never participates in Top 10, Quick Wins, aging, or workload — it no longer lives in
 * the {@code items} collection.
 */
@Document("archivedItems")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivedItem {

    @Id
    private String id;

    private String itemId;
    private String title;
    private String category;
    private String priority;
    private EffortEstimate effortEstimate;
    private Instant dueDate;
    private String groupId;
    private String createdBy;
    private String lastUpdatedBy;
    private String ownerId;
    private Notes notes;
    private Instant createdAt;
    private Instant updatedAt;

    /** RESOLVED / REJECTED / ARCHIVED. */
    private TerminalStatus terminalStatus;
    /** When the terminal transition happened. */
    private Instant completionDate;
    /** When the document left the live table. */
    private Instant movedAt;

    public static ArchivedItem from(Item i, TerminalStatus terminal, String actorId, Instant when) {
        return ArchivedItem.builder()
                .id(i.getId())
                .itemId(i.getItemId())
                .title(i.getTitle())
                .category(i.getCategory())
                .priority(i.getPriority())
                .effortEstimate(i.getEffortEstimate())
                .dueDate(i.getDueDate())
                .groupId(i.getGroupId())
                .createdBy(i.getCreatedBy())
                .lastUpdatedBy(actorId != null ? actorId : i.getLastUpdatedBy())
                .ownerId(i.getOwnerId())
                .notes(i.getNotes())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .terminalStatus(terminal)
                .completionDate(when)
                .movedAt(when)
                .build();
    }
}
