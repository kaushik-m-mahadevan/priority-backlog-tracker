package com.backlogtracker.demo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.backlogtracker.archive.domain.ArchivedItem;
import com.backlogtracker.archive.domain.TerminalStatus;
import com.backlogtracker.archive.repository.ArchivedItemRepository;
import com.backlogtracker.counter.CounterService;
import com.backlogtracker.item.domain.EffortEstimate;
import com.backlogtracker.item.domain.EffortUnit;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Populates a realistic sample backlog for demoing and manual UI testing. Enabled by
 * {@code app.demo-data.enabled=true} (the {@code demo} profile sets this). Idempotent —
 * skips entirely if any live items already exist, so it is safe to leave on.
 *
 * <p>The standalone {@code scripts/seed-demo.mongosh.js} produces the same shape directly
 * against a MongoDB instance without running the app.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final com.backlogtracker.group.repository.GroupRepository groups;
    private final PasswordEncoder passwordEncoder;
    private final ItemRepository items;
    private final ArchivedItemRepository archived;
    private final CounterService counters;
    private final MongoOperations mongo;
    private final org.springframework.core.env.Environment env;

    @Override
    public void run(ApplicationArguments args) {
        String uri = env.getProperty("spring.data.mongodb.uri", "");
        if (!uri.isBlank() && !uri.contains("localhost") && !uri.contains("127.0.0.1")) {
            log.warn("Demo data: MONGODB_URI points at a non-local database ({}). This seeds "
                    + "sample founders with the password 'test123'. Continuing because "
                    + "app.demo-data.enabled=true — disable it for anything real.",
                    uri.replaceAll(":[^:@/]+@", ":***@"));
        }
        if (items.count() > 0) {
            log.info("Demo data: {} items already present — skipping seed", items.count());
            return;
        }
        log.info("Demo data: seeding sample founders, backlog, and archive");

        User alex = ensureUser("Alex Rivera", "alex@demo.test", "ALX");
        User priya = ensureUser("Priya Shah", "priya@demo.test", "PRY");
        User sam = ensureUser("Sam Lee", "sam@demo.test", "SAM");
        String test123 = users.findByEmailIgnoreCase("test123").map(User::getId).orElse(null);

        String groupId = groups.save(com.backlogtracker.group.domain.Group.builder()
                .name("Founders")
                .createdByUserId(test123)
                .memberIds(new ArrayList<>(java.util.List.of(
                        test123, alex.getId(), priya.getId(), sam.getId())))
                .build()).getId();

        // title, category, priority, effort, dueInDays, status, ownerId, createdDaysAgo
        record Spec(String title, String category, String priority, EffortEstimate effort,
                    int dueInDays, ItemStatus status, String ownerId, int createdDaysAgo) {
        }

        List<Spec> specs = List.of(
                new Spec("Fix signup 500 on duplicate email", "Project", "Critical",
                        min(45), -2, ItemStatus.IN_PROGRESS, alex.getId(), 6),
                new Spec("SOC2 evidence collection kickoff", "Admin-Ops", "High",
                        days(3), 9, ItemStatus.BACKLOG, priya.getId(), 12),
                new Spec("Migrate CI to cheaper runners", "Technical Discussion", "Medium",
                        hours(6), 21, ItemStatus.BACKLOG, sam.getId(), 20),
                new Spec("Draft Series A narrative deck", "Project", "High",
                        days(2), 5, ItemStatus.IN_PROGRESS, priya.getId(), 8),
                new Spec("Investigate churn spike in EU", "Research", "Critical",
                        hours(8), 1, ItemStatus.BACKLOG, alex.getId(), 4),
                new Spec("Set up on-call rotation", "Admin-Ops", "Medium",
                        hours(3), 14, ItemStatus.BACKLOG, sam.getId(), 15),
                new Spec("Rewrite onboarding checklist", "Skill-Building", "Low",
                        hours(2), 45, ItemStatus.BACKLOG, null, 38),
                new Spec("Evaluate Postgres vs Mongo for events", "Technical Discussion", "Medium",
                        days(1), 30, ItemStatus.BACKLOG, sam.getId(), 25),
                new Spec("Clean up unused feature flags", "Project", "Low",
                        hours(1), 60, ItemStatus.BACKLOG, null, 41),
                new Spec("Customer interview: Acme Corp", "Research", "High",
                        min(45), -6, ItemStatus.BACKLOG, priya.getId(), 10),
                new Spec("Renew domain + TLS certs", "Admin-Ops", "High",
                        min(30), -20, ItemStatus.BACKLOG, alex.getId(), 22),
                new Spec("Prototype usage-based pricing", "Project", "Medium",
                        days(4), 25, ItemStatus.BACKLOG, priya.getId(), 7),
                new Spec("Write runbook for prod restore", "Admin-Ops", "High",
                        hours(4), 12, ItemStatus.BACKLOG, sam.getId(), 9),
                new Spec("Read 'Designing Data-Intensive Apps' ch.5-7", "Skill-Building", "Low",
                        days(5), 90, ItemStatus.BACKLOG, null, 50),
                new Spec("Reduce cold-start latency on Render", "Project", "Medium",
                        hours(5), 18, ItemStatus.IN_PROGRESS, sam.getId(), 11),
                new Spec("Competitor teardown: Linear", "Research", "Low",
                        hours(2), 40, ItemStatus.BACKLOG, null, 36),
                new Spec("Hire first support contractor", "Admin-Ops", "Critical",
                        days(2), 3, ItemStatus.BACKLOG, priya.getId(), 5),
                new Spec("Add audit log to settings changes", "Project", "Medium",
                        hours(6), -1, ItemStatus.BACKLOG, alex.getId(), 17),
                new Spec("Spike: WebSocket vs SSE for live board", "Technical Discussion", "Low",
                        hours(3), 55, ItemStatus.BACKLOG, null, 33),
                new Spec("Quarterly board update doc", "Admin-Ops", "High",
                        days(1), 7, ItemStatus.BACKLOG, priya.getId(), 6));

        Instant now = Instant.now();
        for (Spec s : specs) {
            Item saved = items.save(Item.builder()
                    .itemId(counters.nextSharedItemId())
                    .title(s.title()).category(s.category()).priority(s.priority())
                    .effortEstimate(s.effort())
                    .dueDate(now.plus(s.dueInDays(), ChronoUnit.DAYS))
                    .status(s.status()).groupId(groupId)
                    .createdBy(test123).lastUpdatedBy(test123)
                    .ownerId(s.ownerId())
                    .build());
            // back-date createdAt past the auditing callback so aging panels have data
            mongo.updateFirst(new Query(Criteria.where("_id").is(saved.getId())),
                    new Update().set("createdAt", now.minus(s.createdDaysAgo(), ChronoUnit.DAYS)),
                    Item.class);
        }

        seedArchived(groupId, "Kill the legacy cron worker", "Project", "Medium", hours(4),
                TerminalStatus.RESOLVED, sam.getId(), 20, 4);
        seedArchived(groupId, "Evaluate Segment for analytics", "Technical Discussion", "Low", hours(2),
                TerminalStatus.REJECTED, priya.getId(), 30, 9);
        seedArchived(groupId, "One-off data backfill for beta users", "Admin-Ops", "High", days(1),
                TerminalStatus.RESOLVED, alex.getId(), 14, 2);
        seedArchived(groupId, "Explore native mobile app", "Research", "Low", days(3),
                TerminalStatus.ARCHIVED, null, 45, 15);

        log.info("Demo data: {} live items, {} archived, {} users",
                items.count(), archived.count(), users.count());
    }

    private User ensureUser(String name, String email, String code) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .name(name).email(email)
                .passwordHash(passwordEncoder.encode("test123"))
                .role(Role.USER).status(AccountStatus.ACTIVE).handle(code.toLowerCase())
                .build()));
    }

    private void seedArchived(String groupId, String title, String category, String priority,
                              EffortEstimate effort, TerminalStatus terminal, String ownerId,
                              int createdDaysAgo, int completedDaysAgo) {
        Instant now = Instant.now();
        archived.save(ArchivedItem.builder()
                .itemId(counters.nextSharedItemId())
                .title(title).category(category).priority(priority).effortEstimate(effort)
                .dueDate(now.minus(completedDaysAgo + 2L, ChronoUnit.DAYS))
                .groupId(groupId).ownerId(ownerId)
                .createdAt(now.minus(createdDaysAgo, ChronoUnit.DAYS))
                .updatedAt(now.minus(completedDaysAgo, ChronoUnit.DAYS))
                .terminalStatus(terminal)
                .completionDate(now.minus(completedDaysAgo, ChronoUnit.DAYS))
                .movedAt(now.minus(completedDaysAgo, ChronoUnit.DAYS))
                .build());
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
