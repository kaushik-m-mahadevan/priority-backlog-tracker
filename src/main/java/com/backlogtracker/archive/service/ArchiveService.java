package com.backlogtracker.archive.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.archive.domain.ArchivedItem;
import com.backlogtracker.archive.domain.TerminalStatus;
import com.backlogtracker.archive.repository.ArchivedItemRepository;
import com.backlogtracker.group.service.GroupService;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.security.AuthUser;

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
    private final TransactionTemplate tx; // null when transactions are not available

    public ArchiveService(ItemRepository items,
                          ArchivedItemRepository archived,
                          GroupService groupService,
                          Clock clock,
                          ObjectProvider<MongoTransactionManager> txManager) {
        this.items = items;
        this.archived = archived;
        this.groupService = groupService;
        this.clock = clock;
        MongoTransactionManager txm = txManager.getIfAvailable();
        this.tx = txm != null ? new TransactionTemplate(txm) : null;
        if (this.tx == null) {
            log.info("ArchiveService: transactions disabled — completion move is sequential");
        }
    }

    public ArchivedItem complete(String id, TerminalStatus terminal, AuthUser actor) {
        Item item = items.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
        groupService.requireMember(item.getGroupId(), actor != null ? actor.id() : null);

        Instant now = clock.instant();
        ArchivedItem toArchive = ArchivedItem.from(item, terminal,
                actor != null ? actor.id() : null, now);

        Runnable move = () -> {
            archived.save(toArchive);
            items.deleteById(id);
        };
        if (tx != null) {
            tx.executeWithoutResult(status -> move.run());
        } else {
            move.run();
        }
        log.info("Item {} ({}) -> {} archived", item.getItemId(), id, terminal);
        return toArchive;
    }
}
