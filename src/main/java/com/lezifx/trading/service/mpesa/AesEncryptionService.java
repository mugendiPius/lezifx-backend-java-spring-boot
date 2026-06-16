package com.lezifx.trading.service.mpesa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class AesEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;

    @Value("${encryption.key}")
    private String encryptionKey;

    private byte[] keyBytes() {
        return Arrays.copyOf(encryptionKey.getBytes(StandardCharsets.UTF_8), 32);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKey key = new SecretKeySpec(keyBytes(), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, output, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return encrypted;
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);

            byte[] iv = Arrays.copyOfRange(decoded, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(decoded, IV_LENGTH, decoded.length);

            SecretKey key = new SecretKeySpec(keyBytes(), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}