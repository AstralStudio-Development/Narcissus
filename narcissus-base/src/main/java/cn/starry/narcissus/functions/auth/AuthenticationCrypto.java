package cn.starry.narcissus.functions.auth;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/** SHA-256 password hashing and RFC 6238-compatible TOTP helpers. */
public final class AuthenticationCrypto {
    public static final int TOTP_ENCRYPTION_KEY_BYTES = 32;

    private static final String TOTP_ENCRYPTION_PREFIX = "NARCISSUS_TOTP_V1:";
    private static final String TOTP_ENCRYPTION_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TOTP_ENCRYPTION_IV_BYTES = 12;
    private static final int TOTP_ENCRYPTION_TAG_BITS = 128;
    private static final byte[] TOTP_ENCRYPTION_AAD = "Narcissus/TOTP/1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private AuthenticationCrypto() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                output.append(Character.forDigit((b >>> 4) & 0x0F, 16));
                output.append(Character.forDigit(b & 0x0F, 16));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    /** Encrypts a TOTP secret for storage using AES-256-GCM with a fresh nonce. */
    public static String encryptTotpSecret(String secret, byte[] key) {
        if (secret == null) {
            throw new IllegalArgumentException("TOTP secret cannot be null");
        }
        validateTotpEncryptionKey(key);

        byte[] iv = new byte[TOTP_ENCRYPTION_IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TOTP_ENCRYPTION_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TOTP_ENCRYPTION_TAG_BITS, iv));
            cipher.updateAAD(TOTP_ENCRYPTION_AAD);
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return TOTP_ENCRYPTION_PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM is unavailable", ex);
        }
    }

    /** Decrypts a versioned AES-GCM TOTP secret from storage. */
    public static String decryptTotpSecret(String encrypted, byte[] key) {
        if (!isEncryptedTotpSecret(encrypted)) {
            throw new IllegalArgumentException("Unsupported TOTP secret format");
        }
        validateTotpEncryptionKey(key);

        final byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(
                    encrypted.substring(TOTP_ENCRYPTION_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid encrypted TOTP secret", ex);
        }
        if (payload.length <= TOTP_ENCRYPTION_IV_BYTES + (TOTP_ENCRYPTION_TAG_BITS / 8)) {
            throw new IllegalArgumentException("Encrypted TOTP secret is truncated");
        }

        byte[] iv = new byte[TOTP_ENCRYPTION_IV_BYTES];
        byte[] ciphertext = new byte[payload.length - iv.length];
        System.arraycopy(payload, 0, iv, 0, iv.length);
        System.arraycopy(payload, iv.length, ciphertext, 0, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance(TOTP_ENCRYPTION_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TOTP_ENCRYPTION_TAG_BITS, iv));
            cipher.updateAAD(TOTP_ENCRYPTION_AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Encrypted TOTP secret failed authentication", ex);
        }
    }

    public static boolean isEncryptedTotpSecret(String value) {
        return value != null && value.startsWith(TOTP_ENCRYPTION_PREFIX);
    }

    private static void validateTotpEncryptionKey(byte[] key) {
        if (key == null || key.length != TOTP_ENCRYPTION_KEY_BYTES) {
            throw new IllegalArgumentException("TOTP encryption key must be 32 bytes");
        }
    }

    public static String generateTotpSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public static String createOtpAuthUri(String username, String secret) {
        String label = urlEncode("Narcissus:" + (username == null ? "" : username));
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=Narcissus&algorithm=SHA1&digits=6&period=30";
    }

    public static boolean verifyTotp(String secret, String code) {
        return verifyTotp(secret, code, System.currentTimeMillis() / 1000L);
    }

    static boolean verifyTotp(String secret, String code, long epochSeconds) {
        if (secret == null || secret.isBlank() || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key;
        try {
            key = decodeBase32(secret);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        long counter = epochSeconds / 30L;
        for (long offset = -1L; offset <= 1L; offset++) {
            String expected = generateTotp(key, counter + offset);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    private static String generateTotp(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate TOTP", ex);
        }
    }

    private static String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(BASE32[(buffer >>> bits) & 0x1F]);
            }
        }
        if (bits > 0) {
            result.append(BASE32[(buffer << (5 - bits)) & 0x1F]);
        }
        return result.toString();
    }

    private static byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        byte[] result = new byte[normalized.length() * 5 / 8];
        long buffer = 0L;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int digit;
            if (c >= 'A' && c <= 'Z') {
                digit = c - 'A';
            } else if (c >= '2' && c <= '7') {
                digit = c - '2' + 26;
            } else {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | digit;
            bits += 5;
            while (bits >= 8) {
                bits -= 8;
                if (index >= result.length) {
                    throw new IllegalArgumentException("Invalid Base32 length");
                }
                result[index++] = (byte) ((buffer >>> bits) & 0xFF);
                buffer = bits == 0 ? 0L : buffer & ((1L << bits) - 1L);
            }
        }
        return result;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
