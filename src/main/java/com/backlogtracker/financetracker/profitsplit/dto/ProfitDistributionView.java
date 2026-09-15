package com.backlogtracker.financetracker.profitsplit.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.backlogtracker.commons.approval.domain.ApprovalStatus;

public record ProfitDistributionView(String requestId, ApprovalStatus status, String orderReference,
                                     BigDecimal totalProfit, List<RecipientAmountView> recipients,
                                     String proposedByUserId, List<String> approvedByUserIds,
                                     List<String> groupMemberIds, Instant createdAt, Instant resolvedAt) {

    public record RecipientAmountView(String personId, BigDecimal amount) {
    }
}
