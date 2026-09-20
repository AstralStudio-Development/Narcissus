package cn.starry.narcissus.util;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.packets.PlayerListEntry;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves Mojang-compatible {@code textures} properties without blocking the selector thread. */
public final class MojangSkinProvider {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final String ASHCON_PROFILE = "https://api.ashcon.app/mojang/v2/user/";
    private static final String PROFILE_BY_NAME = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String LITTLE_SKIN_SESSION_PROFILE =
            "https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/profile/";

    private static final Pattern PROFILE_ID = Pattern.compile(
            "\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final Pattern TEXTURES_PROFILE_ID = Pattern.compile(
            "\\\"profileId\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final Pattern TEXTURES_PROPERTY = Pattern.compile(
            "\\{[^{}]*\\\"name\\\"\\s*:\\s*\\\"textures\\\"[^{}]*\\}");
    private static final Pattern ASHCON_RAW_PROPERTY = Pattern.compile(
            "\\\"raw\\\"\\s*:\\s*\\{([^{}]*)\\}");
    private static final Pattern ASHCON_UUID = Pattern.compile(
            "\\\"uuid\\\"\\s*:\\s*\\\"([0-9a-fA-F-]{32,36})\\\"");
    private static final Pattern ASHCON_USERNAME = Pattern.compile(
            "\\\"username\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASHCON_SKIN_URL = Pattern.compile(
            "\\\"skin\\\"\\s*:\\s*\\{[^{}]*\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\}");
    private static final Pattern ASHCON_CAPE_URL = Pattern.compile(
            "\\\"cape\\\"\\s*:\\s*\\{[^{}]*\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\}");
    private static final Pattern ASHCON_SLIM = Pattern.compile(
            "\\\"slim\\\"\\s*:\\s*(true|false)");
    private static final Pattern PROPERTY_VALUE = Pattern.compile(
            "\\\"value\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PROPERTY_SIGNATURE = Pattern.compile(
            "\\\"signature\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final HttpClient httpClient;
    private final ConcurrentMap<String, CompletableFuture<Optional<PlayerListEntry.ProfileProperty>>> cache =
            new ConcurrentHashMap<>();

    public MojangSkinProvider() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    MojangSkinProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Looks up one textures property through the mainland-friendly fallback first,
     * then Mojang's official endpoints and finally LittleSkin when a UUID is known.
     */
    public CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> lookup(
            String username, UUID uuid, boolean uuidProvided) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String key = uuidProvided && uuid != null
                ? "uuid:" + uuid + "|name:" + name.toLowerCase(Locale.ROOT)
                : "name:" + name.toLowerCase(Locale.ROOT);
        return this.cache.computeIfAbsent(key,
                ignored -> this.lookupUncached(name, uuid, uuidProvided));
    }

    /** Looks up a fixed username with Mojang's signed profile as the preferred source. */
    public CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> lookupByName(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String key = "official-name:" + name.toLowerCase(Locale.ROOT);
        return this.cache.computeIfAbsent(key, ignored ->
                this.lookupOfficialByName(name)
                        .thenCompose(property -> property.isPresent()
                                ? CompletableFuture.completedFuture(property)
                                : this.lookupAshcon(name, null, false))
                        .exceptionally(error -> {
                            Narcissus.LOGGER.fine("Unable to resolve named skin for " + name
                                    + ": " + error.getMessage());
                            return Optional.empty();
                        }));
    }

    private CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> lookupUncached(
            String username, UUID uuid, boolean uuidProvided) {
        CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> result =
                this.lookupAshcon(username, uuid, uuidProvided)
                        .thenCompose(property -> property.isPresent()
                                ? CompletableFuture.completedFuture(property)
                                : this.lookupOfficial(username, uuid, uuidProvided))
                        .thenCompose(property -> property.isPresent()
                                ? CompletableFuture.completedFuture(property)
                                : this.lookupLittleSkin(uuid, uuidProvided));
        return result.exceptionally(error -> {
            Narcissus.LOGGER.fine("Unable to resolve skin for " + username + ": " + error.getMessage());
            return Optional.empty();
        });
    }

    private CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> lookupAshcon(
            String username, UUID uuid, boolean uuidProvided) {
        String subject = uuidProvided && uuid != null ? uuid.toString() : username;
        return this.request(ASHCON_PROFILE + encodePathSegment(subject))
                .thenCompose(body -> {
                    Optional<PlayerListEntry.ProfileProperty> property =
                            body.flatMap(MojangSkinProvider::parseAshconTextures);
                    if (property.isPresent() || !uuidProvided || uuid == null) {
                        return CompletableFuture.completedFuture(property);
                    }
                    // A proxy UUID can be stale or belong to an offline profile;
                    // the game name is the reliable SkinRestorer-style fallback.
                    return this.request(ASHCON_PROFILE + encodePathSegment(username))
                            .thenApply(response -> response.flatMap(MojangSkinProvider::parseAshconTextures));
                });
    }

    private CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> lookupOfficial(
            String username, UUID uuid, boolean uuidProvided) {
        if (uuidProvided && uuid != null) {
            return this.request(this.sessionUrl(uuid))
                    .thenCompose(body -> {
                        Optional<PlayerListEntry.ProfileProperty> property = body.flatMap(MojangSkinProvider::parseTextures);
                        return property.isPresent()
                                ? CompletableFuture.completedFuture(property)
                                : this.lookupOfficialByName(username);
                    });
        }
        return this.lookupOfficialByName(username);
    }

    private CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> lookupOfficialByName(String username) {
        return this.request(PROFILE_BY_NAME + encodePathSegment(username))
                .thenCompose(body -> {
                    Optional<String> id = body.flatMap(MojangSkinProvider::parseProfileId);
                    if (id.isEmpty()) {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    return this.request(this.sessionUrl(id.get()))
                            .thenApply(response -> response.flatMap(MojangSkinProvider::parseTextures));
                });
    }

    private CompletableFuture<Optional<PlayerListEntry.ProfileProperty>> lookupLittleSkin(
            UUID uuid, boolean uuidProvided) {
        if (!uuidProvided || uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.request(LITTLE_SKIN_SESSION_PROFILE + uuid)
                .thenApply(body -> body.flatMap(MojangSkinProvider::parseTextures));
    }

    private CompletableFuture<Optional<String>> request(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Narcissus/1.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            return this.httpClient.sendAsync(request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .orTimeout(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .thenApply(response -> response.statusCode() == 200
                            ? Optional.of(response.body())
                            : Optional.<String>empty())
                    .exceptionally(ignored -> Optional.<String>empty());
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private String sessionUrl(UUID uuid) {
        return this.sessionUrl(uuid.toString());
    }

    private String sessionUrl(String id) {
        return SESSION_PROFILE + formatProfileId(id) + "?unsigned=false";
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String formatProfileId(String id) {
        if (id.length() != 32 || id.indexOf('-') >= 0) {
            return id;
        }
        return id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                + "-" + id.substring(16, 20) + "-" + id.substring(20);
    }

    static Optional<String> parseProfileId(String body) {
        if (body == null) {
            return Optional.empty();
        }
        Matcher matcher = PROFILE_ID.matcher(body);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** Extracts the signed profile UUID embedded in a Mojang textures property. */
    public static Optional<UUID> parseTexturesProfileUuid(PlayerListEntry.ProfileProperty property) {
        if (property == null || property.getValue() == null || property.getValue().isEmpty()) {
            return Optional.empty();
        }
        try {
            String payload = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURES_PROFILE_ID.matcher(payload);
            return matcher.find()
                    ? Optional.of(UUID.fromString(formatProfileId(matcher.group(1))))
                    : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static Optional<PlayerListEntry.ProfileProperty> parseAshconTextures(String body) {
        if (body == null) {
            return Optional.empty();
        }

        // Some Ashcon deployments expose the Mojang-compatible raw property directly.
        Matcher rawMatcher = ASHCON_RAW_PROPERTY.matcher(body);
        if (rawMatcher.find()) {
            Optional<PlayerListEntry.ProfileProperty> raw = parsePropertyBody(rawMatcher.group(1));
            if (raw.isPresent()) {
                return raw;
            }
        }

        Optional<String> skinUrl = findGroup(ASHCON_SKIN_URL, body);
        if (skinUrl.isEmpty()) {
            return Optional.empty();
        }
        String uuid = findGroup(ASHCON_UUID, body).orElse("").replace("-", "");
        String username = findGroup(ASHCON_USERNAME, body).orElse("");
        boolean slim = findGroup(ASHCON_SLIM, body).map(Boolean::parseBoolean).orElse(false);
        String capeUrl = findGroup(ASHCON_CAPE_URL, body).orElse(null);

        StringBuilder textures = new StringBuilder(256)
                .append("{\"timestamp\":").append(System.currentTimeMillis())
                .append(",\"profileId\":\"").append(jsonEscape(uuid))
                .append("\",\"profileName\":\"").append(jsonEscape(username))
                .append("\",\"textures\":{\"SKIN\":{\"url\":\"")
                .append(jsonEscape(skinUrl.get())).append("\"");
        if (slim) {
            textures.append(",\"metadata\":{\"model\":\"slim\"}");
        }
        textures.append("}");
        if (capeUrl != null && !capeUrl.isEmpty()) {
            textures.append(",\"CAPE\":{\"url\":\"")
                    .append(jsonEscape(capeUrl)).append("\"}");
        }
        textures.append("}}");

        String value = Base64.getEncoder().encodeToString(
                textures.toString().getBytes(StandardCharsets.UTF_8));
        return Optional.of(new PlayerListEntry.ProfileProperty("textures", value, null));
    }

    private static Optional<String> findGroup(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    static Optional<PlayerListEntry.ProfileProperty> parseTextures(String body) {
        if (body == null) {
            return Optional.empty();
        }
        Matcher propertyMatcher = TEXTURES_PROPERTY.matcher(body);
        while (propertyMatcher.find()) {
            Optional<PlayerListEntry.ProfileProperty> property = parsePropertyBody(propertyMatcher.group());
            if (property.isPresent()) {
                return property;
            }
        }
        return Optional.empty();
    }

    private static Optional<PlayerListEntry.ProfileProperty> parsePropertyBody(String propertyBody) {
        Matcher valueMatcher = PROPERTY_VALUE.matcher(propertyBody);
        if (!valueMatcher.find()) {
            return Optional.empty();
        }
        Matcher signatureMatcher = PROPERTY_SIGNATURE.matcher(propertyBody);
        String signature = signatureMatcher.find() ? signatureMatcher.group(1) : null;
        return Optional.of(new PlayerListEntry.ProfileProperty("textures", valueMatcher.group(1), signature));
    }
}
