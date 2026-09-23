package com.backlogtracker.financetracker.ledger.service;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.finance.PaymentSyncConsumer;
import com.backlogtracker.commons.finance.PaymentSyncEvent;
import com.backlogtracker.commons.group.domain.Group;

import lombok.RequiredArgsConstructor;

/** ad-1's cross-applet {@link PaymentSyncConsumer} — a thin wrapper around
 *  {@link LedgerEntryService}'s own sync methods. */
@Component
@RequiredArgsConstructor
public class FinanceTrackerPaymentSyncConsumer implements PaymentSyncConsumer {

    private final LedgerEntryService ledgerEntryService;

    @Override
    public String appletKey() {
        return Group.APPLET_FINANCE_TRACKER;
    }

    @Override
    public void onPaymentRecorded(String groupId, String userId, PaymentSyncEvent event) {
        ledgerEntryService.syncFromOrderPayment(groupId, userId, event);
    }

    @Override
    public void onPaymentRemoved(String groupId, String userId, String sourceRef) {
        ledgerEntryService.removeSyncedPayment(groupId, sourceRef);
    }
}
