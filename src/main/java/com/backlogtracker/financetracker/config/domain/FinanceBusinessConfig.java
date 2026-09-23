package com.backlogtracker.financetracker.config.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ad-1: one doc per Finance Tracker group — {@code businessAccountConfigured} gates
 *  whether "Business Account" is selectable as a ledger party at all (spec: only
 *  selectable once this toggle, in Business Settings, is on). Defaults to false/absent;
 *  {@link com.backlogtracker.financetracker.config.service.FinanceBusinessConfigService}
 *  returns a default (unconfigured) view rather than 404 when no doc exists yet. */
@Document("financeBusinessConfig")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceBusinessConfig {

    @Id
    private String groupId;

    private boolean businessAccountConfigured;
}
