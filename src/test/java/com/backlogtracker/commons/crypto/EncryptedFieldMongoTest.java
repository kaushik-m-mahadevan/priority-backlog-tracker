package com.backlogtracker.commons.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Proves the whole round-trip works through the real Spring Data Mongo conversion
 * pipeline, not just AesGcmCipher in isolation: a document saved via MongoTemplate with an
 * {@link EncryptedString} field reads back as the original plaintext through the same
 * template, while the raw BSON actually stored is ciphertext — the thing that actually
 * matters for design §4.5, not just that the cipher class itself works.
 */
@SpringBootTest
class EncryptedFieldMongoTest {

    private static final String COLLECTION = "encryptedFieldMongoTestDocs";

    @Autowired
    MongoOperations mongo;

    @AfterEach
    void cleanUp() {
        mongo.dropCollection(COLLECTION);
    }

    @org.springframework.data.mongodb.core.mapping.Document(COLLECTION)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Probe {
        @Id
        String id;
        EncryptedString secret;
        String plain;
    }

    @Test
    void roundTripsThroughMongoWhileStoringCiphertextAtRest() {
        Probe saved = mongo.save(new Probe(null, EncryptedString.of("+91 98765 43210"), "not sensitive"));

        Probe reloaded = mongo.findById(saved.getId(), Probe.class);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getSecret().value()).isEqualTo("+91 98765 43210");
        assertThat(reloaded.getPlain()).isEqualTo("not sensitive");

        Document raw = mongo.findOne(Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("_id").is(saved.getId())), Document.class, COLLECTION);
        assertThat(raw).isNotNull();
        assertThat(raw.getString("secret")).isNotEqualTo("+91 98765 43210");
        assertThat(raw.getString("plain")).isEqualTo("not sensitive");
    }
}
