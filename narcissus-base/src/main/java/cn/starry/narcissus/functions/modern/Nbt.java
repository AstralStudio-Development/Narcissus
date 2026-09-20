package cn.starry.narcissus.functions.modern;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal network-NBT model + writer for the modern protocol (1.20.2+).
 *
 * Only the subset needed for registry data, dimension/biome/damage_type entries
 * and chat_type is implemented. Values are written using the standard Java NBT
 * binary format (big-endian) via {@link DataOutputStream}.
 *
 * Modern (1.20.2+) network NBT sends the root tag WITHOUT a name: a type byte
 * followed by the payload. {@link #writeNetwork(java.io.OutputStream)} emits that
 * nameless form; {@link #writeNamed(java.io.OutputStream, String)} emits the
 * classic named form used by pre-1.20.2 codecs if ever needed.
 */
public abstract class Nbt {

    public static final byte TAG_END = 0;
    public static final byte TAG_BYTE = 1;
    public static final byte TAG_SHORT = 2;
    public static final byte TAG_INT = 3;
    public static final byte TAG_LONG = 4;
    public static final byte TAG_FLOAT = 5;
    public static final byte TAG_DOUBLE = 6;
    public static final byte TAG_BYTE_ARRAY = 7;
    public static final byte TAG_STRING = 8;
    public static final byte TAG_LIST = 9;
    public static final byte TAG_COMPOUND = 10;
    public static final byte TAG_INT_ARRAY = 11;
    public static final byte TAG_LONG_ARRAY = 12;

    public abstract byte id();

    public abstract void writePayload(DataOutputStream out) throws IOException;

    /** Writes this tag as a nameless network tag (modern form): type byte + payload. */
    public void writeNetwork(java.io.OutputStream stream) throws IOException {
        DataOutputStream out = new DataOutputStream(stream);
        out.writeByte(id());
        writePayload(out);
    }

    /** Serialises this tag to a byte[] in the nameless network form. */
    public byte[] toNetworkBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeNetwork(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ---- concrete tag types ----

    public static final class NbtByte extends Nbt {
        public final byte value;
        public NbtByte(int value) { this.value = (byte) value; }
        public byte id() { return TAG_BYTE; }
        public void writePayload(DataOutputStream out) throws IOException { out.writeByte(value); }
    }

    public static final class NbtInt extends Nbt {
        public final int value;
        public NbtInt(int value) { this.value = value; }
        public byte id() { return TAG_INT; }
        public void writePayload(DataOutputStream out) throws IOException { out.writeInt(value); }
    }

    public static final class NbtLong extends Nbt {
        public final long value;
        public NbtLong(long value) { this.value = value; }
        public byte id() { return TAG_LONG; }
        public void writePayload(DataOutputStream out) throws IOException { out.writeLong(value); }
    }

    public static final class NbtFloat extends Nbt {
        public final float value;
        public NbtFloat(float value) { this.value = value; }
        public byte id() { return TAG_FLOAT; }
        public void writePayload(DataOutputStream out) throws IOException { out.writeFloat(value); }
    }

    public static final class NbtDouble extends Nbt {
        public final double value;
        public NbtDouble(double value) { this.value = value; }
        public byte id() { return TAG_DOUBLE; }
        public void writePayload(DataOutputStream out) throws IOException { out.writeDouble(value); }
    }

    public static final class NbtString extends Nbt {
        public final String value;
        public NbtString(String value) { this.value = value; }
        public byte id() { return TAG_STRING; }
        public void writePayload(DataOutputStream out) throws IOException { out.writeUTF(value); }
    }

    public static final class NbtList extends Nbt {
        private byte elementType = TAG_END;
        public final List<Nbt> items = new ArrayList<>();
        public byte id() { return TAG_LIST; }
        public NbtList add(Nbt tag) {
            if (elementType == TAG_END) elementType = tag.id();
            else if (elementType != tag.id())
                throw new IllegalArgumentException("Heterogeneous NBT list");
            items.add(tag);
            return this;
        }
        public void writePayload(DataOutputStream out) throws IOException {
            out.writeByte(items.isEmpty() ? TAG_END : elementType);
            out.writeInt(items.size());
            for (Nbt item : items) item.writePayload(out);
        }
    }

    public static final class NbtCompound extends Nbt {
        public final Map<String, Nbt> entries = new LinkedHashMap<>();
        public byte id() { return TAG_COMPOUND; }
        public NbtCompound put(String name, Nbt tag) { entries.put(name, tag); return this; }
        public NbtCompound putByte(String name, int v) { return put(name, new NbtByte(v)); }
        public NbtCompound putInt(String name, int v) { return put(name, new NbtInt(v)); }
        public NbtCompound putLong(String name, long v) { return put(name, new NbtLong(v)); }
        public NbtCompound putFloat(String name, float v) { return put(name, new NbtFloat(v)); }
        public NbtCompound putDouble(String name, double v) { return put(name, new NbtDouble(v)); }
        public NbtCompound putString(String name, String v) { return put(name, new NbtString(v)); }
        public NbtCompound putBool(String name, boolean v) { return put(name, new NbtByte(v ? 1 : 0)); }
        public void writePayload(DataOutputStream out) throws IOException {
            for (Map.Entry<String, Nbt> e : entries.entrySet()) {
                out.writeByte(e.getValue().id());
                out.writeUTF(e.getKey());
                e.getValue().writePayload(out);
            }
            out.writeByte(TAG_END);
        }
    }
}
