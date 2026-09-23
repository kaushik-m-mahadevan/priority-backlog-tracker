package com.backlogtracker.backlogtracker.archive.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.backlogtracker.archive.domain.ArchivedItem;
import com.backlogtracker.backlogtracker.archive.domain.TerminalStatus;
import com.backlogtracker.backlogtracker.archive.repository.ArchivedItemRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.security.AuthUser;

import lombok.extern.slf4j.Slf4j;

/**
 * The unidirectional completion move (design §24): reaching a terminal state physically
 * relocates the document from {@code items} to {@code archivedItems} in one operation —
 * it is not a status flag. There is no reverse path.
 *
 * <p>When a {@link MongoTransactionManager} is present (replica set / Atlas — enabled via
 * {@code app.mongo.transactions.enabled=true}) the move is atomic. Otherwise it degrades
 * to sequential save-then-delete, which is acceptable for local/embedded development.
 */
@Service
@Slf4j
public class ArchiveService {

    private final ItemRepository items;
    private final ArchivedItemRepository archived;
    private final GroupService groupService;
    private final Clock clock;
    private final MongoOperations mongo;
    private final TransactionTemplate tx; // null when transactions are not available

    public ArchiveService(ItemRepository items,
                          ArchivedItemRepository archived,
                          GroupService groupService,
                          Clock clock,
                          MongoOperations mongo,
                          ObjectProvider<MongoTransactionManager> txManager) {
        this.items = items;
        this.archived = archived;
        this.groupService = groupService;
        this.clock = clock;
        this.mongo = mongo;
        MongoTransactionManager txm = txManager.getIfAvailable();
        this.tx = txm != null ? new TransactionTemplate(txm) : null;
        if (this.tx == null) {
            log.info("ArchiveService: transactions disabled — completion move is sequential");
        }
    }

    public ArchivedItem complete(String id, TerminalStatus terminal, AuthUser actor) {
        Item item = items.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
        String actorId = actor != null ? actor.id() : null;
        groupService.requireMember(item.getGroupId(), actorId);

        // Archiving an active item needs every member's approval — unless you're the only
        // member (then it's unanimous by definition). RESOLVED / REJECTED stay direct.
        if (terminal == TerminalStatus.ARCHIVED
                && groupService.memberCount(item.getGroupId()) > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Archiving an active item needs every member's approval — "
                            + "raise an archive request instead");
        }
        return move(item, terminal, actorId);
    }

    /** Final archive after an archive request ({@link com.backlogtracker.backlogtracker.archive.service.ArchiveRequestService})
     *  is unanimously approved. The approval already stands in for the membership check. */
    public ArchivedItem completeApproved(String itemId, String actorId) {
        Item item = items.findById(itemId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + itemId));
        return move(item, TerminalStatus.ARCHIVED, actorId);
    }

    /** Removes the item from {@code items} via an atomic {@code findAndRemove} first, and
     *  only archives it if that removal actually found something — so if this same item is
     *  being completed twice at once (e.g. the last approval on an archive request is
     *  double-submitted), only the caller that actually wins the removal creates an
     *  {@link ArchivedItem}; the loser's {@code findAndRemove} returns {@code null} instead
     *  of racing a second, duplicate archived copy into existence. */
    private ArchivedItem move(Item item, TerminalStatus terminal, String actorId) {
        Instant now = clock.instant();
        Supplier<ArchivedItem> move = () -> {
            Item removed = mongo.findAndRemove(Query.query(Criteria.where("id").is(item.getId())), Item.class);
            if (removed == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This item was already completed by someone else");
            }
            return archived.save(ArchivedItem.from(removed, terminal, actorId, now));
        };
        ArchivedItem toArchive = tx != null ? tx.execute(status -> move.get()) : move.get();
        log.info("Item {} ({}) -> {} archived", item.getItemId(), item.getId(), terminal);
        return toArchive;
    }
}
