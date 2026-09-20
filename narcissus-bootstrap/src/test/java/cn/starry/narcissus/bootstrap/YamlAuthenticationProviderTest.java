package cn.starry.narcissus.bootstrap;

import cn.starry.narcissus.functions.auth.AuthenticationCrypto;
import cn.starry.narcissus.functions.auth.AuthenticationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YamlAuthenticationProviderTest {
    @Test
    void normalAccountUsesSha256AndRoundTripsThroughYaml(@TempDir Path tempDir) throws Exception {
        Path accounts = tempDir.resolve("accounts.yml");
        Path staff = tempDir.resolve("staffAccount.yml");
        Files.writeString(staff, "staffAccounts:\n  - StaffUser\n");

        YamlAuthenticationProvider provider = new YamlAuthenticationProvider(
                accounts, staff, "CODE"::equals);
        assertEquals(AuthenticationResult.Status.REGISTERED,
                provider.register("PlayerOne", "password" ).getStatus());
        assertEquals(AuthenticationResult.Status.INVALID_PASSWORD,
                provider.login("PlayerOne", "wrong").getStatus());
        assertEquals(AuthenticationResult.Status.LOGIN_SUCCESS,
                provider.login("playerone", "password").getStatus());

        String stored = Files.readString(accounts);
        assertTrue(stored.contains("password_sha256:"));
        assertFalse(stored.contains("password\n"));

        YamlAuthenticationProvider reloaded = new YamlAuthenticationProvider(
                accounts, staff, "CODE"::equals);
        assertEquals(AuthenticationResult.Status.LOGIN_SUCCESS,
                reloaded.login("PlayerOne", "password").getStatus());
    }

    @Test
    void staffRegistrationRequiresConsoleCode(@TempDir Path tempDir) throws Exception {
        Path accounts = tempDir.resolve("accounts.yml");
        Path staff = tempDir.resolve("staffAccount.yml");
        Files.writeString(staff, "staffAccounts:\n  - StaffUser\n");
        YamlAuthenticationProvider provider = new YamlAuthenticationProvider(
                accounts, staff, "CODE"::equals);

        assertEquals(AuthenticationResult.Status.INVALID_STAFF_CODE,
                provider.register("StaffUser", "wrong").getStatus());
        assertEquals(AuthenticationResult.Status.STAFF_REGISTERED,
                provider.register("StaffUser", "CODE").getStatus());
        assertEquals(AuthenticationResult.Status.TWO_FACTOR_SETUP_READY,
                provider.beginTwoFactorSetup("staffuser").getStatus());
        assertEquals(AuthenticationResult.Status.TWO_FACTOR_NOT_CONFIGURED,
                provider.login("StaffUser", "password").getStatus());
    }

    @Test
    void totpSecretIsEncryptedAndCanBeReloaded(@TempDir Path tempDir) throws Exception {
        Path accounts = tempDir.resolve("accounts.yml");
        Path staff = tempDir.resolve("staffAccount.yml");
        Path key = tempDir.resolve("totp.key");
        Files.writeString(staff, "staffAccounts:\n  - StaffUser\n");

        YamlAuthenticationProvider provider = new YamlAuthenticationProvider(
                accounts, staff, key, "CODE"::equals);
        assertEquals(AuthenticationResult.Status.STAFF_REGISTERED,
                provider.register("StaffUser", "CODE").getStatus());
        String secret = provider.beginTwoFactorSetup("StaffUser").getValue();
        String stored = Files.readString(accounts);

        assertTrue(Files.exists(key));
        assertTrue(stored.contains("NARCISSUS_TOTP_V1:"));
        assertFalse(stored.contains(secret));
        assertFalse(stored.contains("otpauth://"));

        YamlAuthenticationProvider reloaded = new YamlAuthenticationProvider(
                accounts, staff, key, "CODE"::equals);
        assertEquals(secret, reloaded.beginTwoFactorSetup("StaffUser").getValue());
    }

    @Test
    void legacyPlaintextTotpSecretIsMigratedOnLoad(@TempDir Path tempDir) throws Exception {
        Path accounts = tempDir.resolve("accounts.yml");
        Path staff = tempDir.resolve("staffAccount.yml");
        Path key = tempDir.resolve("totp.key");
        String secret = AuthenticationCrypto.generateTotpSecret();
        Files.writeString(staff, "staffAccounts:\n  - StaffUser\n");
        Files.writeString(accounts, "accounts:\n"
                + "  staffuser:\n"
                + "    username: StaffUser\n"
                + "    password_sha256: ''\n"
                + "    staff: true\n"
                + "    totp_secret: " + secret + "\n"
                + "    totp_enabled: false\n");

        YamlAuthenticationProvider provider = new YamlAuthenticationProvider(
                accounts, staff, key, "CODE"::equals);
        String migrated = Files.readString(accounts);

        assertTrue(migrated.contains("NARCISSUS_TOTP_V1:"));
        assertFalse(migrated.contains(secret));
        assertEquals(secret, provider.beginTwoFactorSetup("StaffUser").getValue());
    }

    @Test
    void missingKeyRejectsEncryptedAccounts(@TempDir Path tempDir) throws Exception {
        Path accounts = tempDir.resolve("accounts.yml");
        Path staff = tempDir.resolve("staffAccount.yml");
        Path key = tempDir.resolve("totp.key");
        Files.writeString(staff, "staffAccounts:\n  - StaffUser\n");
        Files.writeString(accounts, "accounts:\n  staffuser:\n    totp_secret: NARCISSUS_TOTP_V1:missing\n");

        assertThrows(IOException.class, () -> new YamlAuthenticationProvider(
                accounts, staff, key, "CODE"::equals));
        assertFalse(Files.exists(key));
    }
}
