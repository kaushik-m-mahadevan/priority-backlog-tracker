package com.backlogtracker.ordertracker.customer.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.backlogtracker.commons.crypto.EncryptedString;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer of one group's Order Tracker business (design §4). Scoped per group — a
 * customer belongs to exactly one business, the same as everything else in Order Tracker
 * (platform integration decision: every group is its own separate business).
 *
 * <p>Every field here except {@code id}, {@code groupId}, {@code acquisitionChannel}, and
 * {@code firstContactDate} is {@link EncryptedString} per design §4.5's explicit
 * encryption-scope table — those four are excluded on purpose (they're not free-text PII,
 * and the business view needs to filter/sort by channel and contact date at the database
 * level, which ciphertext can't support).
 */
@Document("orderTrackerCustomers")
@CompoundIndexes({
        @CompoundIndex(name = "group_emailHash", def = "{'groupId': 1, 'emailHash': 1}", sparse = true),
        @CompoundIndex(name = "group_igHash", def = "{'groupId': 1, 'instagramHandleHash': 1}", sparse = true),
        @CompoundIndex(name = "group_phoneHash", def = "{'groupId': 1, 'contactNumberHash': 1}", sparse = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private EncryptedString name;
    private EncryptedString contactNumber;
    /** Optional. */
    private EncryptedString email;
    private EncryptedString instagramHandle;

    /**
     * Blind-index hashes (see {@code BlindIndexService}) letting the app look a customer up
     * by email/IG handle/phone despite those fields being encrypted at rest — not unique
     * (two customers could plausibly share a number, e.g. a shared family contact), just
     * indexed for an exact-match lookup. Recomputed whenever the underlying field changes.
     */
    private String emailHash;
    private String instagramHandleHash;
    private String contactNumberHash;

    private AcquisitionChannel acquisitionChannel;
    private Instant firstContactDate;

    /** Default shipping address; an order may override this with its own (design §5). */
    private EncryptedString shippingAddress;

    /** Optional. */
    private EncryptedString notes;
}
