package com.backlogtracker.financetracker.ledger.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.financetracker.ledger.domain.LedgerEntry;

public interface LedgerEntryRepository extends MongoRepository<LedgerEntry, String> {

    List<LedgerEntry> findByGroupIdOrderByDateDesc(String groupId);

    Optional<LedgerEntry> findByGroupIdAndSourceRef(String groupId, String sourceRef);

    void deleteByGroupIdAndSourceRef(String groupId, String sourceRef);
}
