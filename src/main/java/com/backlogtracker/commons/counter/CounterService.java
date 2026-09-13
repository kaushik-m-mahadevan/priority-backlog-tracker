package com.backlogtracker.commons.counter;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.backlogtracker.commons.counter.domain.Counter;

import lombok.RequiredArgsConstructor;

/**
 * Race-condition-safe sequence generation (design §20). Each call is a single atomic
 * {@code findAndModify} with {@code $inc} and upsert, so concurrent callers can never
 * receive the same value.
 */
@Service
@RequiredArgsConstructor
public class CounterService {

    static final String SHARED_KEY = "shared";

    private static final FindAndModifyOptions INCREMENT_AND_RETURN =
            new FindAndModifyOptions().returnNew(true).upsert(true);

    private final MongoOperations mongo;

    /** Returns the next value for {@code key}, starting at 1. */
    public long next(String key) {
        Counter counter = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(key)),
                new Update().inc("seq", 1),
                INCREMENT_AND_RETURN,
                Counter.class);
        // returnNew + upsert guarantees a non-null result
        return counter.getSeq();
    }

    /** Next globally sequential item id, e.g. {@code ITM-001} (§20). */
    public String nextSharedItemId() {
        return "ITM-%03d".formatted(next(SHARED_KEY));
    }
}
