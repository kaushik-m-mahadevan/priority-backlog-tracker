package com.backlogtracker.counter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;

import com.backlogtracker.counter.domain.Counter;

@SpringBootTest
class CounterServiceTest {

    @Autowired
    CounterService counterService;

    @Autowired
    MongoOperations mongo;

    @BeforeEach
    void clear() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Counter.class);
    }

    @Test
    void incrementsSequentiallyFromOne() {
        assertThat(counterService.next("seq-test")).isEqualTo(1L);
        assertThat(counterService.next("seq-test")).isEqualTo(2L);
        assertThat(counterService.next("seq-test")).isEqualTo(3L);
    }

    @Test
    void countersAreIndependentPerKey() {
        counterService.next("a");
        counterService.next("a");
        assertThat(counterService.next("b")).isEqualTo(1L);
        assertThat(counterService.next("a")).isEqualTo(3L);
    }

    @Test
    void concurrentCallsProduceNoDuplicates() throws Exception {
        int n = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Long>> tasks = IntStream.range(0, n)
                    .<Callable<Long>>mapToObj(i -> () -> counterService.next("race"))
                    .toList();
            List<Long> results = pool.invokeAll(tasks).stream()
                    .map(CounterServiceTest::get)
                    .collect(Collectors.toList());

            assertThat(results).hasSize(n);
            assertThat(results.stream().distinct().sorted().toList())
                    .isEqualTo(IntStream.rangeClosed(1, n).asLongStream().boxed().toList());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void formatsItemIds() {
        assertThat(counterService.nextSharedItemId()).isEqualTo("ITM-001");
        assertThat(counterService.nextSharedItemId()).isEqualTo("ITM-002");
    }

    private static Long get(Future<Long> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
