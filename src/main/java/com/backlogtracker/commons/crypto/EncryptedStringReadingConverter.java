package com.backlogtracker.commons.crypto;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import lombok.RequiredArgsConstructor;

@ReadingConverter
@RequiredArgsConstructor
public class EncryptedStringReadingConverter implements Converter<String, EncryptedString> {

    private final AesGcmCipher cipher;

    @Override
    public EncryptedString convert(String source) {
        return EncryptedString.of(cipher.decrypt(source));
    }
}
