package com.backlogtracker.materialinventory.transfer.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.materialinventory.transfer.domain.TransferRequest;

public interface TransferRequestRepository extends MongoRepository<TransferRequest, String> {

    List<TransferRequest> findByGroupId(String groupId);
}
