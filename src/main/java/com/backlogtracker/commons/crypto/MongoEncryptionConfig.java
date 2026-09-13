package com.backlogtracker.commons.crypto;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
@EnableConfigurationProperties(EncryptionProperties.class)
public class MongoEncryptionConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions(AesGcmCipher cipher) {
        return new MongoCustomConversions(List.of(
                new EncryptedStringWritingConverter(cipher),
                new EncryptedStringReadingConverter(cipher)));
    }
}
