package com.backlogtracker.backlogtracker.demo;

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

import com.backlogtracker.backlogtracker.archive.domain.ArchivedItem;
import com.backlogtracker.backlogtracker.archive.domain.TerminalStatus;
import com.backlogtracker.backlogtracker.archive.repository.ArchivedItemRepository;
import com.backlogtracker.commons.counter.CounterService;
import com.backlogtracker.backlogtracker.item.domain.EffortEstimate;
import com.backlogtracker.backlogtracker.item.domain.EffortUnit;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.financetracker.ledger.domain.LedgerEntryType;
import com.backlogtracker.financetracker.ledger.domain.SplitPartyType;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest.ShareInput;
import com.backlogtracker.financetracker.ledger.service.LedgerEntryService;
import com.backlogtracker.ordertracker.customer.domain.AcquisitionChannel;
import com.backlogtracker.ordertracker.customer.dto.CustomerView;
import com.backlogtracker.ordertracker.customer.dto.UpsertCustomerRequest;
import com.backlogtracker.ordertracker.customer.service.CustomerService;
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.service.BusinessConfigService;
import com.backlogtracker.ordertracker.master.service.CreatorService;
import com.backlogtracker.ordertracker.order.domain.Order.MaterialKind;
import com.backlogtracker.ordertracker.order.domain.OrderStatus;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.MandatoryItemInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.SplitLineInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.VariantInput;
import com.backlogtracker.ordertracker.order.dto.OrderView;
import com.backlogtracker.ordertracker.order.dto.UpdateOrderStatusRequest;
import com.backlogtracker.ordertracker.order.service.OrderService;
import com.backlogtracker.productcatalog.colorway.dto.CreateColorwayRequest;
import com.backlogtracker.productcatalog.colorway.service.ColorwayService;

import java.math.BigDecimal;

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
    private final com.backlogtracker.commons.group.repository.GroupRepository groups;
    private final PasswordEncoder passwordEncoder;
    private final ItemRepository items;
    private final ArchivedItemRepository archived;
    private final CounterService counters;
    private final MongoOperations mongo;
    private final org.springframework.core.env.Environment env;
    private final BusinessConfigService businessConfigService;
    private final CreatorService creatorService;
    private final CustomerService customerService;
    private final OrderService orderService;
    private final ColorwayService colorwayService;
    private final LedgerEntryService ledgerEntryService;

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

        // .categories left unset — Group's @Builder.Default seeds the standard starter list.
        String groupId = groups.save(com.backlogtracker.commons.group.domain.Group.builder()
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

        // Each applet is its own separate "business"/workspace, keyed by appletKey — a
        // group can't double as both a Backlog Tracker group and an Order Tracker one, so
        // Order Tracker/Product Catalog/Finance Tracker each need their own "Founders"
        // group here, not the backlog one above.
        List<String> allFounders = List.of(test123, alex.getId(), priya.getId(), sam.getId());
        String otGroupId = seedAppletGroup("Founders", allFounders,
                com.backlogtracker.commons.group.domain.Group.APPLET_ORDER_TRACKER);
        String ftGroupId = seedAppletGroup("Founders", allFounders,
                com.backlogtracker.commons.group.domain.Group.APPLET_FINANCE_TRACKER);
        String pcGroupId = seedAppletGroup("Founders", allFounders,
                com.backlogtracker.commons.group.domain.Group.APPLET_PRODUCT_CATALOG);

        seedOrderTrackerCatalogAndFinance(otGroupId, ftGroupId, pcGroupId, test123, alex, priya, sam);
    }

    private String seedAppletGroup(String name, List<String> memberIds, String appletKey) {
        return groups.save(com.backlogtracker.commons.group.domain.Group.builder()
                .name(name)
                .createdByUserId(memberIds.get(0))
                .appletKey(appletKey)
                .memberIds(new ArrayList<>(memberIds))
                .build()).getId();
    }

    /** Order Tracker / Product Catalog / Finance Tracker sample data — each in its own
     *  applet-scoped group (see {@link #seedAppletGroup}) — built through each domain's
     *  own service layer (not raw repository saves) since these documents have real
     *  invariants (auto-assigned order numbers/codes, encrypted customer fields, computed
     *  cost estimates) that only the service layer knows how to satisfy correctly. */
    private void seedOrderTrackerCatalogAndFinance(String groupId, String financeGroupId, String catalogGroupId,
                                                    String test123, User alex, User priya, User sam) {
        businessConfigService.get(groupId, alex.getId()); // seeds default BusinessConfig if absent

        Creator alexCreator = creatorService.upsertMyProfile(groupId, alex.getId(), alex.getName(), "Bengaluru", 3.0);
        Creator priyaCreator = creatorService.upsertMyProfile(groupId, priya.getId(), priya.getName(), "Mumbai", 2.5);
        creatorService.upsertMyProfile(groupId, sam.getId(), sam.getName(), "Chennai", 4.0);
        // The admin signed into every demo group (test123) needs a Creator profile too,
        // or Order Tracker gates the whole workspace behind a first-time setup prompt.
        creatorService.upsertMyProfile(groupId, test123, "Test User", "Bengaluru", 4.0);

        CustomerView meera = customerService.create(groupId, alex.getId(), new UpsertCustomerRequest(
                "Meera Krishnan", "9876543210", "meera.k@example.com", "@meera.makes",
                AcquisitionChannel.INSTAGRAM, Instant.now().minus(60, ChronoUnit.DAYS),
                "12 Lake View Road, Bengaluru", null));
        CustomerView rahul = customerService.create(groupId, alex.getId(), new UpsertCustomerRequest(
                "Rahul Nair", "9123456780", null, null,
                AcquisitionChannel.WORD_OF_MOUTH, Instant.now().minus(35, ChronoUnit.DAYS),
                "45 MG Road, Chennai", "Prefers WhatsApp updates"));
        CustomerView pooja = customerService.create(groupId, priya.getId(), new UpsertCustomerRequest(
                "Pooja Desai", "9988776655", "pooja.d@example.com", "@poojawears",
                AcquisitionChannel.REFERRAL, Instant.now().minus(10, ChronoUnit.DAYS),
                "7 Marine Drive, Mumbai", null));

        colorwayService.create(catalogGroupId, alex.getId(), new CreateColorwayRequest(
                "Sunset Coral", "Coral / Cream", 450.0, "Best-seller — pairs well with cream trims", List.of()));
        colorwayService.create(catalogGroupId, priya.getId(), new CreateColorwayRequest(
                "Sage Meadow", "Sage Green", 400.0, null, List.of()));

        OrderView order1 = orderService.create(groupId, alex.getId(), new CreateOrderRequest(
                meera.id(), "INDIVIDUAL", alexCreator.getId(),
                "Coral crochet tote bag", Instant.now().minus(6, ChronoUnit.DAYS),
                Instant.now().plus(9, ChronoUnit.DAYS), null, List.of(), 2.0, null, null,
                List.of(new MandatoryItemInput(MaterialKind.YARN, "Coral cotton yarn", 4, 120, null, null, null)),
                List.of(), List.of(), null, List.of(), 6.0, 2.0,
                null, null, 0));
        orderService.updateStatus(groupId, alex.getId(), order1.id(), new UpdateOrderStatusRequest(OrderStatus.IN_PROGRESS));

        orderService.create(groupId, priya.getId(), new CreateOrderRequest(
                rahul.id(), "INDIVIDUAL", priyaCreator.getId(),
                "Custom amigurumi elephant", Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now().plus(14, ChronoUnit.DAYS), null, List.of(), 1.5, null, null,
                List.of(new MandatoryItemInput(MaterialKind.YARN, "Grey acrylic yarn", 2, 90, null, null, null)),
                List.of(), List.of(), null, List.of(), 4.0, 1.5,
                null, null, 0));

        OrderView bulkOrder = orderService.create(groupId, alex.getId(), new CreateOrderRequest(
                pooja.id(), "BULK", alexCreator.getId(),
                "Wedding favour coasters (set of 8)", Instant.now().minus(4, ChronoUnit.DAYS),
                Instant.now().plus(20, ChronoUnit.DAYS), null, List.of(), 1.0, null, null,
                null, null, null, null, null, 0, 0,
                List.of(
                        new VariantInput("v1", "Sage Meadow coaster", 5, List.of(), List.of(), List.of(), null, List.of(),
                                3.0, 1.0, List.of(new SplitLineInput(alexCreator.getId(), 5))),
                        new VariantInput("v2", "Sunset Coral coaster", 3, List.of(), List.of(), List.of(), null, List.of(),
                                2.0, 1.0, List.of(new SplitLineInput(priyaCreator.getId(), 3)))),
                alexCreator.getId(), 0));
        orderService.updateStatus(groupId, alex.getId(), bulkOrder.id(), new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));

        ledgerEntryService.create(financeGroupId, alex.getId(), new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Yarn restock — coral cotton + grey acrylic", new BigDecimal("2400"),
                alex.getId(), List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));
        ledgerEntryService.create(financeGroupId, priya.getId(), new CreateLedgerEntryRequest(
                LedgerEntryType.EXPENSE, "Packaging boxes (50 pack)", new BigDecimal("850"),
                priya.getId(), List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));
        ledgerEntryService.create(financeGroupId, alex.getId(), new CreateLedgerEntryRequest(
                LedgerEntryType.INCOME, "Advance for wedding favour order", new BigDecimal("3000"),
                alex.getId(), List.of(new ShareInput(SplitPartyType.BUSINESS, null, BigDecimal.ONE))));

        log.info("Demo data: seeded Order Tracker (3 creators, 3 customers, 3 orders), "
                + "Product Catalog (2 colorways), and Finance Tracker (3 ledger entries)");
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
