package cn.starry.narcissus.functions.packets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable player profile data used by protocol-specific player-info packets. */
public final class PlayerListEntry {
    /** A Mojang profile property, such as the signed {@code textures} property. */
    public static final class ProfileProperty {
        private final String name;
        private final String value;
        private final String signature;

        public ProfileProperty(String name, String value, String signature) {
            this.name = Objects.requireNonNull(name, "name");
            this.value = Objects.requireNonNull(value, "value");
            this.signature = signature;
        }

        public String getName() {
            return this.name;
        }

        public String getValue() {
            return this.value;
        }

        public String getSignature() {
            return this.signature;
        }
    }

    private final UUID uuid;
    private final String username;
    private final int gameMode;
    private final List<ProfileProperty> properties;
    private final boolean listed;

    public PlayerListEntry(UUID uuid, String username, int gameMode) {
        this(uuid, username, gameMode, Collections.emptyList(), true);
    }

    public PlayerListEntry(UUID uuid, String username, int gameMode, List<ProfileProperty> properties) {
        this(uuid, username, gameMode, properties, true);
    }

    public PlayerListEntry(UUID uuid, String username, int gameMode,
                           List<ProfileProperty> properties, boolean listed) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.username = username == null ? "" : username;
        this.gameMode = gameMode;
        if (properties == null || properties.isEmpty()) {
            this.properties = Collections.emptyList();
        } else {
            this.properties = Collections.unmodifiableList(new ArrayList<>(properties));
        }
        this.listed = listed;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public String getUsername() {
        return this.username;
    }

    public int getGameMode() {
        return this.gameMode;
    }

    public List<ProfileProperty> getProperties() {
        return this.properties;
    }

    public boolean isListed() {
        return this.listed;
    }
}
