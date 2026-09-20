package cn.starry.narcissus.bootstrap;

import cn.starry.narcissus.functions.packets.PlayerListEntry;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Reads Bungee's login GameProfile without linking against proxy internals. */
final class BungeeSkinProfileReader {
    private BungeeSkinProfileReader() {
    }

    static Optional<PlayerListEntry.ProfileProperty> read(ProxiedPlayer player) {
        if (player == null || player.getPendingConnection() == null) {
            return Optional.empty();
        }
        try {
            Object pendingConnection = player.getPendingConnection();
            Object loginProfile = readMember(pendingConnection, "getLoginProfile", "loginProfile");
            if (loginProfile == null) {
                return Optional.empty();
            }

            Object properties = loginProfile.getClass().isArray()
                    ? loginProfile
                    : readMember(loginProfile, "getProperties", "properties");
            for (Object property : values(properties)) {
                String name = readString(property, "getName", "name");
                if (!"textures".equalsIgnoreCase(name)) {
                    continue;
                }
                String value = readString(property, "getValue", "value");
                if (value == null || value.isEmpty()) {
                    continue;
                }
                String signature = readString(property, "getSignature", "signature");
                return Optional.of(new PlayerListEntry.ProfileProperty("textures", value, signature));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The proxy's internal profile type changes between Bungee builds;
            // the core's HTTP skin providers remain the fallback path.
        }
        return Optional.empty();
    }

    private static Object readMember(Object target, String... names)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (String name : names) {
                try {
                    Method method = type.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    // Try the next method/field and superclass.
                }
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try the next method/field and superclass.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static String readString(Object target, String... names)
            throws ReflectiveOperationException {
        Object value = readMember(target, names);
        return value == null ? null : String.valueOf(value);
    }

    private static List<Object> values(Object container) {
        if (container == null) {
            return Collections.emptyList();
        }
        if (container instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            for (Object value : iterable) {
                values.add(value);
            }
            return values;
        }
        if (container.getClass().isArray()) {
            int length = Array.getLength(container);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(container, i));
            }
            return values;
        }
        return Collections.singletonList(container);
    }
}
