package cn.starry.narcissus.bootstrap;

import cn.starry.narcissus.functions.auth.AuthenticationCrypto;
import cn.starry.narcissus.functions.auth.AuthenticationProvider;
import cn.starry.narcissus.functions.auth.AuthenticationResult;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** YAML-backed password and Staff/TOTP account provider for the Bungee plugin. */
public final class YamlAuthenticationProvider implements AuthenticationProvider {
    private static final String TOTP_KEY_PREFIX = "NARCISSUS_TOTP_KEY_V1:";
    private static final List<String> DEFAULT_STAFF = Arrays.asList(
            "Stalyer", "Starry_Killer", "CloudForeal", "Qlickly_");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path accountsFile;
    private final Path staffFile;
    private final Path totpKeyFile;
    private final byte[] totpEncryptionKey;
    private final Predicate<String> staffCodeValidator;
    private final Yaml yaml = new Yaml();
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Set<String> staffAccounts = new LinkedHashSet<>();

    public YamlAuthenticationProvider(Path accountsFile, Path staffFile,
                                      Predicate<String> staffCodeValidator) throws IOException {
        this(accountsFile, staffFile, accountsFile.resolveSibling("totp.key"), staffCodeValidator);
    }

    public YamlAuthenticationProvider(Path accountsFile, Path staffFile, Path totpKeyFile,
                                      Predicate<String> staffCodeValidator) throws IOException {
        this.accountsFile = accountsFile;
        this.staffFile = staffFile;
        this.totpKeyFile = totpKeyFile;
        this.staffCodeValidator = staffCodeValidator;
        this.totpEncryptionKey = loadOrCreateTotpEncryptionKey();
        loadStaffAccounts();
        loadAccounts();
    }

    @Override
    public synchronized AuthenticationResult register(String username, String credential) {
        String key = normalize(username);
        if (key.isEmpty()) {
            return AuthenticationResult.of(AuthenticationResult.Status.INVALID_PASSWORD);
        }
        if (this.accounts.containsKey(key)) {
            return AuthenticationResult.of(AuthenticationResult.Status.ALREADY_REGISTERED);
        }

        boolean staff = this.staffAccounts.contains(key);
        if (staff) {
            if (credential == null || credential.isBlank()) {
                return AuthenticationResult.of(AuthenticationResult.Status.STAFF_CODE_REQUIRED);
            }
            if (this.staffCodeValidator == null || !this.staffCodeValidator.test(credential.trim())) {
                return AuthenticationResult.of(AuthenticationResult.Status.INVALID_STAFF_CODE);
            }
            Account account = new Account(username, null, true, null, false);
            this.accounts.put(key, account);
            if (!saveAccounts()) {
                this.accounts.remove(key);
                return AuthenticationResult.of(AuthenticationResult.Status.STORAGE_ERROR);
            }
            return AuthenticationResult.of(AuthenticationResult.Status.STAFF_REGISTERED);
        }

        if (credential == null || credential.length() < 4) {
            return AuthenticationResult.of(AuthenticationResult.Status.INVALID_PASSWORD);
        }
        Account account = new Account(username, AuthenticationCrypto.sha256(credential),
                false, null, false);
        this.accounts.put(key, account);
        if (!saveAccounts()) {
            this.accounts.remove(key);
            return AuthenticationResult.of(AuthenticationResult.Status.STORAGE_ERROR);
        }
        return AuthenticationResult.of(AuthenticationResult.Status.REGISTERED);
    }

    @Override
    public synchronized AuthenticationResult login(String username, String credential) {
        String key = normalize(username);
        Account account = this.accounts.get(key);
        if (account == null) {
            return AuthenticationResult.of(AuthenticationResult.Status.NOT_REGISTERED);
        }

        boolean staff = account.staff || this.staffAccounts.contains(key);
        if (staff) {
            if (!account.totpEnabled || account.totpSecret == null || account.totpSecret.isBlank()) {
                return AuthenticationResult.of(AuthenticationResult.Status.TWO_FACTOR_NOT_CONFIGURED);
            }
            return AuthenticationCrypto.verifyTotp(account.totpSecret, credential)
                    ? AuthenticationResult.of(AuthenticationResult.Status.LOGIN_SUCCESS)
                    : AuthenticationResult.of(AuthenticationResult.Status.INVALID_TWO_FACTOR);
        }

        String expected = AuthenticationCrypto.sha256(credential == null ? "" : credential);
        return AuthenticationCrypto.constantTimeEquals(account.passwordSha256, expected)
                ? AuthenticationResult.of(AuthenticationResult.Status.LOGIN_SUCCESS)
                : AuthenticationResult.of(AuthenticationResult.Status.INVALID_PASSWORD);
    }

    @Override
    public synchronized AuthenticationResult beginTwoFactorSetup(String username) {
        String key = normalize(username);
        if (!this.staffAccounts.contains(key) && !hasStaffAccount(key)) {
            return AuthenticationResult.of(AuthenticationResult.Status.NOT_STAFF_ACCOUNT);
        }
        Account account = this.accounts.get(key);
        if (account == null) {
            return AuthenticationResult.of(AuthenticationResult.Status.NOT_REGISTERED);
        }

        boolean changed = false;
        if (account.totpSecret == null || account.totpSecret.isBlank()) {
            account.totpSecret = AuthenticationCrypto.generateTotpSecret();
            account.totpEnabled = false;
            changed = true;
        }
        if (changed && !saveAccounts()) {
            return AuthenticationResult.of(AuthenticationResult.Status.STORAGE_ERROR);
        }
        return AuthenticationResult.of(AuthenticationResult.Status.TWO_FACTOR_SETUP_READY,
                account.totpSecret);
    }

    @Override
    public synchronized AuthenticationResult confirmTwoFactor(String username, String code) {
        String key = normalize(username);
        Account account = this.accounts.get(key);
        if (account == null) {
            return AuthenticationResult.of(AuthenticationResult.Status.NOT_REGISTERED);
        }
        if (!account.staff && !this.staffAccounts.contains(key)) {
            return AuthenticationResult.of(AuthenticationResult.Status.NOT_STAFF_ACCOUNT);
        }
        if (account.totpSecret == null || account.totpSecret.isBlank()) {
            return AuthenticationResult.of(AuthenticationResult.Status.TWO_FACTOR_NOT_CONFIGURED);
        }
        if (!AuthenticationCrypto.verifyTotp(account.totpSecret, code)) {
            return AuthenticationResult.of(AuthenticationResult.Status.INVALID_TWO_FACTOR);
        }

        boolean previous = account.totpEnabled;
        account.staff = true;
        account.totpEnabled = true;
        if (!saveAccounts()) {
            account.totpEnabled = previous;
            return AuthenticationResult.of(AuthenticationResult.Status.STORAGE_ERROR);
        }
        return AuthenticationResult.of(AuthenticationResult.Status.TWO_FACTOR_ENABLED);
    }

    @Override
    public synchronized boolean isRegistered(String username) {
        return this.accounts.containsKey(normalize(username));
    }

    @Override
    public synchronized boolean isStaffAccount(String username) {
        String key = normalize(username);
        Account account = this.accounts.get(key);
        return this.staffAccounts.contains(key) || (account != null && account.staff);
    }

    @Override
    public synchronized boolean isTwoFactorConfigured(String username) {
        Account account = this.accounts.get(normalize(username));
        return account != null && account.totpEnabled && account.totpSecret != null
                && !account.totpSecret.isBlank();
    }

    private boolean hasStaffAccount(String key) {
        Account account = this.accounts.get(key);
        return account != null && account.staff;
    }

    private void loadAccounts() throws IOException {
        ensureFile(this.accountsFile, "accounts:\n");
        boolean needsMigration = false;
        try (Reader reader = Files.newBufferedReader(this.accountsFile)) {
            Object loaded = this.yaml.load(reader);
            if (!(loaded instanceof Map<?, ?> root)) {
                return;
            }
            Object rawAccounts = root.get("accounts");
            if (!(rawAccounts instanceof Map<?, ?> values)) {
                return;
            }
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                String key = normalize(String.valueOf(entry.getKey()));
                if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                    continue;
                }
                String username = stringValue(raw.get("username"), String.valueOf(entry.getKey()));
                String password = stringValue(raw.get("password_sha256"), null);
                boolean staff = booleanValue(raw.get("staff"), this.staffAccounts.contains(key));
                String storedSecret = stringValue(raw.get("totp_secret"), null);
                String secret = storedSecret;
                if (storedSecret != null && !storedSecret.isBlank()) {
                    if (AuthenticationCrypto.isEncryptedTotpSecret(storedSecret)) {
                        try {
                            secret = AuthenticationCrypto.decryptTotpSecret(
                                    storedSecret, this.totpEncryptionKey);
                        } catch (IllegalArgumentException ex) {
                            throw new IOException("Failed to decrypt totp_secret for account " + key, ex);
                        }
                    } else {
                        needsMigration = true;
                    }
                }
                boolean totpEnabled = booleanValue(raw.get("totp_enabled"), false);
                this.accounts.put(key, new Account(username, password, staff, secret, totpEnabled));
            }
        }
        if (needsMigration && !saveAccounts()) {
            throw new IOException("Failed to migrate plaintext totp_secret values");
        }
    }

    private byte[] loadOrCreateTotpEncryptionKey() throws IOException {
        Path parent = this.totpKeyFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(this.totpKeyFile)) {
            String stored = Files.readString(this.totpKeyFile).trim();
            if (stored.startsWith(TOTP_KEY_PREFIX)) {
                stored = stored.substring(TOTP_KEY_PREFIX.length());
            }
            final byte[] key;
            try {
                key = Base64.getDecoder().decode(stored);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid TOTP encryption key file: " + this.totpKeyFile, ex);
            }
            if (key.length != AuthenticationCrypto.TOTP_ENCRYPTION_KEY_BYTES) {
                throw new IOException("TOTP encryption key must be 32 bytes: " + this.totpKeyFile);
            }
            return key;
        }

        if (accountsContainEncryptedTotpSecret()) {
            throw new IOException("Encrypted totp_secret values exist but the key file is missing: "
                    + this.totpKeyFile);
        }

        byte[] key = new byte[AuthenticationCrypto.TOTP_ENCRYPTION_KEY_BYTES];
        RANDOM.nextBytes(key);
        String encoded = TOTP_KEY_PREFIX + Base64.getEncoder().encodeToString(key)
                + System.lineSeparator();
        try {
            Files.writeString(this.totpKeyFile, encoded, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ex) {
            return loadOrCreateTotpEncryptionKey();
        }
        return key;
    }

    private boolean accountsContainEncryptedTotpSecret() throws IOException {
        return Files.exists(this.accountsFile)
                && Files.readString(this.accountsFile).contains("NARCISSUS_TOTP_V1:");
    }

    private void loadStaffAccounts() throws IOException {
        ensureFile(this.staffFile, defaultStaffYaml());
        try (Reader reader = Files.newBufferedReader(this.staffFile)) {
            Object loaded = this.yaml.load(reader);
            if (loaded instanceof Map<?, ?> root) {
                Object raw = root.get("staffAccounts");
                if (raw == null) {
                    raw = root.get("staff_accounts");
                }
                if (raw instanceof Iterable<?> values) {
                    for (Object value : values) {
                        String key = normalize(String.valueOf(value));
                        if (!key.isEmpty()) {
                            this.staffAccounts.add(key);
                        }
                    }
                }
            }
        }
        if (this.staffAccounts.isEmpty()) {
            for (String name : DEFAULT_STAFF) {
                this.staffAccounts.add(normalize(name));
            }
        }
    }

    private boolean saveAccounts() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Account> entry : this.accounts.entrySet()) {
            Account account = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("username", account.username);
            value.put("password_sha256", account.passwordSha256 == null ? "" : account.passwordSha256);
            value.put("staff", account.staff);
            String storedSecret = account.totpSecret == null || account.totpSecret.isBlank()
                    ? ""
                    : AuthenticationCrypto.encryptTotpSecret(account.totpSecret, this.totpEncryptionKey);
            value.put("totp_secret", storedSecret);
            value.put("totp_enabled", account.totpEnabled);
            values.put(entry.getKey(), value);
        }
        root.put("accounts", values);

        try {
            Path parent = this.accountsFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = this.accountsFile.resolveSibling(this.accountsFile.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                this.yaml.dump(root, writer);
            }
            try {
                Files.move(temporary, this.accountsFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, this.accountsFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static void ensureFile(Path file, String defaultContent) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file)) {
            Files.writeString(file, defaultContent, StandardOpenOption.CREATE_NEW);
        }
    }

    private static String defaultStaffYaml() {
        StringBuilder output = new StringBuilder("staffAccounts:\n");
        for (String name : DEFAULT_STAFF) {
            output.append("  - ").append(name).append('\n');
        }
        return output.toString();
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isEmpty() && fallback != null ? fallback : text;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private static final class Account {
        private final String username;
        private final String passwordSha256;
        private boolean staff;
        private String totpSecret;
        private boolean totpEnabled;

        private Account(String username, String passwordSha256, boolean staff,
                        String totpSecret, boolean totpEnabled) {
            this.username = username;
            this.passwordSha256 = passwordSha256;
            this.staff = staff;
            this.totpSecret = totpSecret;
            this.totpEnabled = totpEnabled;
        }
    }
}
