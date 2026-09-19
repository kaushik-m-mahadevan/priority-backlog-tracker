package com.backlogtracker.backlogtracker.insights.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.backlogtracker.backlogtracker.archive.domain.ArchivedItem;
import com.backlogtracker.backlogtracker.archive.domain.TerminalStatus;

import lombok.RequiredArgsConstructor;

/** Feeds the grove: how many items were finished recently, and when the last one was. */
@Service
@RequiredArgsConstructor
public class CompletionStatsService {

    /** "Finished" excludes {@link TerminalStatus#REJECTED} on purpose — a rejected item
     *  didn't get done, it got dropped. */
    private static final List<String> FINISHED_STATUSES =
            List.of(TerminalStatus.RESOLVED.name(), TerminalStatus.ARCHIVED.name());

    private final MongoOperations mongo;
    private final Clock clock;

    public record CompletionStats(long count, int days, Instant lastCompletedAt) {
    }

    public CompletionStats recent(String groupId, int days) {
        int d = Math.min(Math.max(1, days), 365);
        Criteria done = new Criteria().andOperator(
                Criteria.where("groupId").is(groupId),
                Criteria.where("terminalStatus").in(FINISHED_STATUSES));

        long count = mongo.count(
                Query.query(new Criteria().andOperator(done,
                        Criteria.where("completionDate").gte(clock.instant().minus(d, ChronoUnit.DAYS)))),
                ArchivedItem.class);

        Instant last = mongo.find(
                        Query.query(done).with(Sort.by(Sort.Direction.DESC, "completionDate")).limit(1),
                        ArchivedItem.class)
                .stream().findFirst().map(ArchivedItem::getCompletionDate).orElse(null);

        return new CompletionStats(count, d, last);
    }
}
