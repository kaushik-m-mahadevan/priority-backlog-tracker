package com.backlogtracker.financetracker.ledger.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.financetracker.ledger.domain.LedgerEntry;

public interface LedgerEntryRepository extends MongoRepository<LedgerEntry, String> {

    List<LedgerEntry> findByGroupIdOrderByCreatedAtDesc(String groupId);
}
