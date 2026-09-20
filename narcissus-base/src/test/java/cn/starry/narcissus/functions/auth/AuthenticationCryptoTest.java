package cn.starry.narcissus.functions.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationCryptoTest {
    @Test
    void sha256MatchesKnownDigest() {
        assertEquals("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
                AuthenticationCrypto.sha256("password"));
    }

    @Test
    void totpMatchesRfc6238Sha1Vector() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertTrue(AuthenticationCrypto.verifyTotp(secret, "005924", 1_234_567_890L));
        assertFalse(AuthenticationCrypto.verifyTotp(secret, "000000", 1_234_567_890L));
    }

    @Test
    void generatedSecretProducesMicrosoftAuthenticatorUri() {
        String secret = AuthenticationCrypto.generateTotpSecret();
        assertEquals(32, secret.length());
        assertTrue(AuthenticationCrypto.createOtpAuthUri("Starry_Killer", secret)
                .startsWith("otpauth://totp/Narcissus%3AStarry_Killer?secret="));
    }

    @Test
    void totpSecretEncryptionRoundTripsWithFreshCiphertext() {
        byte[] key = new byte[AuthenticationCrypto.TOTP_ENCRYPTION_KEY_BYTES];
        key[0] = 7;
        key[key.length - 1] = 23;
        String secret = "JBSWY3DPEHPK3PXP";

        String first = AuthenticationCrypto.encryptTotpSecret(secret, key);
        String second = AuthenticationCrypto.encryptTotpSecret(secret, key);

        assertTrue(AuthenticationCrypto.isEncryptedTotpSecret(first));
        assertNotEquals(secret, first);
        assertNotEquals(first, second);
        assertEquals(secret, AuthenticationCrypto.decryptTotpSecret(first, key));
        assertEquals(secret, AuthenticationCrypto.decryptTotpSecret(second, key));
    }

    @Test
    void tamperedOrWrongKeyTotpCiphertextIsRejected() {
        byte[] key = new byte[AuthenticationCrypto.TOTP_ENCRYPTION_KEY_BYTES];
        key[0] = 11;
        String encrypted = AuthenticationCrypto.encryptTotpSecret("JBSWY3DPEHPK3PXP", key);
        int tamperIndex = "NARCISSUS_TOTP_V1:".length() + 5;
        char original = encrypted.charAt(tamperIndex);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, tamperIndex) + replacement
                + encrypted.substring(tamperIndex + 1);

        assertThrows(IllegalArgumentException.class,
                () -> AuthenticationCrypto.decryptTotpSecret(tampered, key));

        byte[] wrongKey = key.clone();
        wrongKey[0] = 12;
        assertThrows(IllegalArgumentException.class,
                () -> AuthenticationCrypto.decryptTotpSecret(encrypted, wrongKey));
    }
}
