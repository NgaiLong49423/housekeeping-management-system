package com.housekeeping.management.security;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Field-level encryption for the short-lived payout account payload. */
@Component
public class PayoutAccountDetailsCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    public PayoutAccountDetailsCipher(@Value("${payout.encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(AccountDetails details) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream fields = new java.io.DataOutputStream(payload)) {
                fields.writeUTF(details.bankName());
                fields.writeUTF(details.accountHolder());
                fields.writeUTF(details.accountNumber());
            }
            byte[] ciphertext = cipher.doFinal(payload.toByteArray());
            byte[] output = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, output, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (GeneralSecurityException | java.io.IOException failure) {
            throw new IllegalStateException("Không thể bảo vệ thông tin nhận tiền.", failure);
        }
    }

    public AccountDetails decrypt(String encrypted) {
        try {
            byte[] input = Base64.getDecoder().decode(encrypted);
            if (input.length <= NONCE_BYTES) throw new IllegalArgumentException("Dữ liệu mã hóa không hợp lệ.");
            byte[] nonce = Arrays.copyOfRange(input, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(input, NONCE_BYTES, input.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
            try (java.io.DataInputStream fields = new java.io.DataInputStream(new java.io.ByteArrayInputStream(plaintext))) {
                AccountDetails details = new AccountDetails(fields.readUTF(), fields.readUTF(), fields.readUTF());
                if (fields.available() != 0) throw new IllegalArgumentException("Dữ liệu tài khoản không hợp lệ.");
                return details;
            }
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Không thể giải mã thông tin nhận tiền. Hãy kiểm tra khóa mã hóa cấu hình.", failure);
        }
    }

    private SecretKeySpec key() {
        if (encodedKey == null || encodedKey.isBlank())
            throw new IllegalStateException("Chưa cấu hình PAYOUT_ENCRYPTION_KEY; không thể nhận thông tin ngân hàng.");
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException invalidKey) {
            throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY phải là Base64 của khóa ngẫu nhiên 32 byte.", invalidKey);
        }
    }

    public record AccountDetails(String bankName, String accountHolder, String accountNumber) { }
}
