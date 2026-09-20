package cn.starry.narcissus.util;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.PlayerListEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Shared Bungee-to-Limbo payload for forwarding the authenticated textures property. */
public final class SkinPropertyPayload {
    public static final String CHANNEL = "narcissus:skin";
    public static final String LEGACY_CHANNEL = "NarcissusSkin";

    private SkinPropertyPayload() {
    }

    public static boolean isSkinChannel(String channel) {
        return CHANNEL.equalsIgnoreCase(channel) || LEGACY_CHANNEL.equalsIgnoreCase(channel);
    }

    public static byte[] encode(PlayerListEntry.ProfileProperty property) throws IOException {
        if (property == null || property.getValue() == null || property.getValue().isEmpty()) {
            throw new IllegalArgumentException("textures property is required");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PacketSerializer serializer = new PacketSerializer(output);
        serializer.writeString(property.getValue());
        serializer.writeBoolean(property.getSignature() != null && !property.getSignature().isEmpty());
        if (property.getSignature() != null && !property.getSignature().isEmpty()) {
            serializer.writeString(property.getSignature());
        }
        return output.toByteArray();
    }

    public static PlayerListEntry.ProfileProperty decode(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            return null;
        }
        PacketSerializer serializer = new PacketSerializer(new ByteArrayInputStream(payload));
        String value = serializer.readString();
        if (value.isEmpty()) {
            return null;
        }
        String signature = null;
        if (serializer.available() > 0 && serializer.readBoolean()) {
            if (serializer.available() <= 0) {
                return null;
            }
            signature = serializer.readString();
        }
        return new PlayerListEntry.ProfileProperty("textures", value, signature);
    }
}
