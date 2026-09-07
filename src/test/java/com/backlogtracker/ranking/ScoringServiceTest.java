package com.backlogtracker.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.item.domain.EffortEstimate;
import com.backlogtracker.item.domain.EffortUnit;
import com.backlogtracker.item.domain.Item;

class ScoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ScoringService scoring = new ScoringService(clock);
    private final AppConfig cfg = AppConfig.defaults(); // weights 0.333/0.333/0.334, window 42, cap 43200

    private static Item item(String priority, EffortEstimate effort, Instant due, Instant createdAt) {
        return Item.builder()
                .priority(priority).effortEstimate(effort).dueDate(due).createdAt(createdAt)
                .build();
    }

    private Instant inDays(long d) {
        return NOW.plus(d, ChronoUnit.DAYS);
    }

    @Test
    void priorityFactorIsValueOverMax() {
        Item critical = item("Critical", min(30), inDays(10), NOW);
        Item high = item("High", min(30), inDays(10), NOW);
        Item medium = item("Medium", min(30), inDays(10), NOW);
        Item low = item("Low", min(30), inDays(10), NOW);

        assertThat(scoring.priorityFactor(critical, cfg)).isEqualTo(1.0);
        assertThat(scoring.priorityFactor(high, cfg)).isEqualTo(0.75);
        assertThat(scoring.priorityFactor(medium, cfg)).isEqualTo(0.5);
        assertThat(scoring.priorityFactor(low, cfg)).isEqualTo(0.25);
    }

    @Test
    void urgencyFactorRunsFromZeroAtWindowEdgeToOneAtDueToday() {
        // window = urgencyWindowDays * 3 = 42
        assertThat(scoring.urgencyFactor(item("Low", min(30), inDays(42), NOW), cfg))
                .isCloseTo(0.0, within(1e-9));
        assertThat(scoring.urgencyFactor(item("Low", min(30), inDays(21), NOW), cfg))
                .isCloseTo(0.5, within(1e-9));
        assertThat(scoring.urgencyFactor(item("Low", min(30), inDays(0), NOW), cfg))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void overdueItemsCapAtUrgencyOneNeverAbove() {
        double f = scoring.urgencyFactor(item("Low", min(30), inDays(-30), NOW), cfg);
        assertThat(f).isEqualTo(1.0);
    }

    @Test
    void beyondWindowUrgencyStaysAtZero() {
        assertThat(scoring.urgencyFactor(item("Low", min(30), inDays(365), NOW), cfg))
                .isEqualTo(0.0);
    }

    @Test
    void effortFactorRunsFromNearOneForTinyTasksToZeroAtCap() {
        // cap = effortCapDays(30) * 24 * 60 = 43200 minutes
        assertThat(scoring.effortFactor(item("Low", min(15), inDays(10), NOW), cfg))
                .isCloseTo(1.0 - 15.0 / 43200.0, within(1e-9));
        assertThat(scoring.effortFactor(item("Low", days(30), inDays(10), NOW), cfg))
                .isCloseTo(0.0, within(1e-9));
        assertThat(scoring.effortFactor(item("Low", hours(12), inDays(10), NOW), cfg))
                .isCloseTo(1.0 - 720.0 / 43200.0, within(1e-9));
    }

    @Test
    void sortScoreIsWeightedSumOfFactors() {
        AppConfig custom = AppConfig.defaults();
        custom.setPriorityWeight(0.5);
        custom.setUrgencyWeight(0.3);
        custom.setEffortWeight(0.2);

        Item it = item("High", min(30), inDays(21), NOW); // pf .75, uf .5, ef ~ 1 - 30/43200
        ScoringService.Score s = scoring.score(it, custom);

        double expected = 0.5 * 0.75 + 0.3 * 0.5 + 0.2 * (1.0 - 30.0 / 43200.0);
        assertThat(s.sortScore()).isCloseTo(expected, within(1e-9));
    }

    @Test
    void tieBreakPrefersSmallerEffortThenOlderItem() {
        // Force an exact sortScore tie: identical priority + due date, effort differs.
        Item quick = item("Medium", min(15), inDays(10), Instant.parse("2026-01-10T00:00:00Z"));
        Item slow = item("Medium", hours(3), inDays(10), Instant.parse("2026-01-01T00:00:00Z"));

        List<ScoringService.Scored> ranked = scoring.rank(List.of(slow, quick), cfg);
        assertThat(ranked.get(0).item()).isSameAs(quick); // smaller effort wins the tie

        // Same effort now -> older createdAt wins.
        Item older = item("Medium", min(30), inDays(10), Instant.parse("2026-01-01T00:00:00Z"));
        Item newer = item("Medium", min(30), inDays(10), Instant.parse("2026-01-09T00:00:00Z"));
        List<ScoringService.Scored> byAge = scoring.rank(List.of(newer, older), cfg);
        assertThat(byAge.get(0).item()).isSameAs(older);
    }

    @Test
    void rankOrdersBySortScoreDescending() {
        Item top = item("Critical", min(15), inDays(0), NOW);
        Item mid = item("Medium", hours(2), inDays(20), NOW);
        Item bottom = item("Low", days(20), inDays(120), NOW);

        List<ScoringService.Scored> ranked = scoring.rank(List.of(mid, bottom, top), cfg);

        assertThat(ranked).extracting(s -> s.item()).containsExactly(top, mid, bottom);
        assertThat(ranked.get(0).score().sortScore())
                .isGreaterThan(ranked.get(1).score().sortScore());
        assertThat(ranked.get(1).score().sortScore())
                .isGreaterThan(ranked.get(2).score().sortScore());
    }

    @Test
    void quickWinsOrderIsEffortAscThenSortScoreDesc() {
        Item tinyLowValue = item("Low", min(15), inDays(200), NOW);      // 15 min
        Item tinyHighValue = item("Critical", min(15), inDays(0), NOW);  // 15 min, higher score
        Item small = item("Critical", min(30), inDays(0), NOW);          // 30 min
        Item big = item("Critical", hours(4), inDays(0), NOW);           // 240 min

        List<ScoringService.Scored> qw =
                scoring.rankBy(List.of(big, small, tinyLowValue, tinyHighValue),
                        cfg, ScoringService.QUICK_WINS_ORDER);

        assertThat(qw).extracting(ScoringService.Scored::item)
                .containsExactly(tinyHighValue, tinyLowValue, small, big);
    }

    private static EffortEstimate min(int v) {
        return new EffortEstimate(v, EffortUnit.MINUTES);
    }

    private static EffortEstimate hours(int v) {
        return new EffortEstimate(v, EffortUnit.HOURS);
    }

    private static EffortEstimate days(int v) {
        return new EffortEstimate(v, EffortUnit.DAYS);
    }
}
