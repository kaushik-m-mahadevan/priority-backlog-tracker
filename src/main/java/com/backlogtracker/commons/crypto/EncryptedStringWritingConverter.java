package com.backlogtracker.commons.crypto;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import lombok.RequiredArgsConstructor;

@WritingConverter
@RequiredArgsConstructor
public class EncryptedStringWritingConverter implements Converter<EncryptedString, String> {

    private final AesGcmCipher cipher;

    @Override
    public String convert(EncryptedString source) {
        return cipher.encrypt(source.value());
    }
}
