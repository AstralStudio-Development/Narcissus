package cn.starry.narcissus.functions.packets;

import cn.starry.narcissus.functions.world.BlockPosition;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 *
 * Hardened for the plugin rewrite: proper EOF handling, unsigned reads,
 * bounded string/array reads and a readFully helper so partial reads on the
 * accumulated frame buffer cannot silently corrupt packet decoding.
 */
public class PacketSerializer
{
    /** Vanilla protocol string cap (32767 UTF-16 units, up to 4 bytes each) plus headroom. */
    public static final int DEFAULT_MAX_STRING_BYTES = 32767 * 4;

    private final InputStream inputStream;
    private final OutputStream outputStream;
    private int maxStringBytes = DEFAULT_MAX_STRING_BYTES;

    public PacketSerializer(InputStream inputStream)
    {
        this.inputStream = inputStream;
        this.outputStream = null;
    }

    public PacketSerializer(OutputStream outputStream)
    {
        this.inputStream = null;
        this.outputStream = outputStream;
    }

    public void setMaxStringBytes(int maxStringBytes)
    {
        this.maxStringBytes = maxStringBytes;
    }

    private void ensureInputStream()
    {
        if (this.inputStream == null)
            throw new IllegalStateException("Trying to read an output PacketSerializer");
    }

    private void ensureOutputStream()
    {
        if (this.outputStream == null)
            throw new IllegalStateException("Trying to write on input PacketSerializer");
    }

    public int readUnsignedByte() throws IOException
    {
        this.ensureInputStream();
        int value = this.inputStream.read();
        if (value == -1)
            throw new EOFException("Unexpected end of stream");
        return value;
    }

    public byte readByte() throws IOException
    {
        return (byte) this.readUnsignedByte();
    }

    public boolean readBoolean() throws IOException
    {
        return this.readUnsignedByte() != 0;
    }

    public int readVarInt() throws IOException
    {
        this.ensureInputStream();

        int out = 0;
        int bytes = 0;
        int in;
        while (true)
        {
            in = this.readUnsignedByte();
            out |= (in & 0x7F) << (bytes++ * 7);
            if (bytes > 5)
                throw new IOException("VarInt too big");
            if ((in & 0x80) != 0x80)
                break;
        }
        return out;
    }

    public long readVarLong() throws IOException
    {
        this.ensureInputStream();

        long out = 0;
        int bytes = 0;
        int in;
        while (true)
        {
            in = this.readUnsignedByte();
            out |= (long) (in & 0x7F) << (bytes++ * 7);
            if (bytes > 10)
                throw new IOException("VarLong too big");
            if ((in & 0x80) != 0x80)
                break;
        }
        return out;
    }

    public short readShort() throws IOException
    {
        this.ensureInputStream();
        int high = this.readUnsignedByte();
        int low = this.readUnsignedByte();
        return (short) ((high << 8) | low);
    }

    public int readInt() throws IOException
    {
        this.ensureInputStream();
        int result = 0;
        for (int i = 0; i < 4; i++)
            result = (result << 8) | this.readUnsignedByte();
        return result;
    }

    public long readLong() throws IOException
    {
        this.ensureInputStream();
        long result = 0;
        for (int i = 0; i < 8; i++)
            result = (result << 8) | (long) this.readUnsignedByte();
        return result;
    }

    public byte[] readBytes(int length) throws IOException
    {
        this.ensureInputStream();
        if (length < 0)
            throw new IOException("Negative array length");
        byte[] bytes = new byte[length];
        int read = 0;
        while (read < length)
        {
            int count = this.inputStream.read(bytes, read, length - read);
            if (count == -1)
                throw new EOFException("Unexpected end of stream while reading " + length + " bytes");
            read += count;
        }
        return bytes;
    }

    private byte[] readByteArray() throws IOException
    {
        this.ensureInputStream();
        int size = this.readVarInt();
        if (size < 0 || size > this.maxStringBytes)
            throw new IOException("Declared byte array length out of bounds: " + size);
        return this.readBytes(size);
    }

    public String readString() throws IOException
    {
        this.ensureInputStream();
        return new String(this.readByteArray(), StandardCharsets.UTF_8);
    }

    public void writeVarInt(int value) throws IOException
    {
        this.ensureOutputStream();
        int part;
        while (true)
        {
            part = value & 0x7F;
            value >>>= 7;
            if (value != 0)
                part |= 0x80;
            this.outputStream.write(part);
            if (value == 0)
                break;
        }
    }

    public void writeVarLong(long value) throws IOException
    {
        this.ensureOutputStream();
        int part;
        while (true)
        {
            part = (int) (value & 0x7F);
            value >>>= 7;
            if (value != 0)
                part |= 0x80;
            this.outputStream.write(part);
            if (value == 0)
                break;
        }
    }

    public void writeByteArray(byte[] bytes) throws IOException
    {
        this.ensureOutputStream();
        this.writeVarInt(bytes.length);
        this.outputStream.write(bytes);
    }

    public void writeRawBytes(byte[] bytes) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(bytes);
    }

    public java.util.UUID readUUID() throws IOException
    {
        this.ensureInputStream();
        long most = this.readLong();
        long least = this.readLong();
        return new java.util.UUID(most, least);
    }

    public void writeUUID(java.util.UUID uuid) throws IOException
    {
        this.ensureOutputStream();
        this.writeLong(uuid.getMostSignificantBits());
        this.writeLong(uuid.getLeastSignificantBits());
    }

    /** Writes a length-prefixed (VarInt) BitSet as an array of longs, per modern protocol. */
    public void writeBitSet(java.util.BitSet bitSet) throws IOException
    {
        this.ensureOutputStream();
        long[] words = bitSet.toLongArray();
        this.writeVarInt(words.length);
        for (long word : words)
            this.writeLong(word);
    }

    /** Writes a fixed-size (unprefixed) long array, used for light data masks/arrays framing. */
    public void writeLongArray(long[] values) throws IOException
    {
        this.ensureOutputStream();
        this.writeVarInt(values.length);
        for (long value : values)
            this.writeLong(value);
    }

    /** Writes an optional value with a boolean presence prefix. */
    public void writeOptionalString(String value) throws IOException
    {
        this.ensureOutputStream();
        if (value == null)
        {
            this.writeBoolean(false);
        }
        else
        {
            this.writeBoolean(true);
            this.writeString(value);
        }
    }

    /**
     * Writes pre-serialized network bytes verbatim (e.g. an offline-generated
     * nameless NBT compound or registry entry). Modern registry/NBT payloads are
     * shipped as fixtures and written as-is, so we do not implement a full NBT
     * encoder at runtime.
     */
    public void writePreSerialized(byte[] bytes) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(bytes);
    }

    public void writeString(String string) throws IOException
    {
        this.ensureOutputStream();
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        this.writeByteArray(bytes);
    }

    public void writeInt(int value) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(ByteBuffer.allocate(4).putInt(value).array());
    }

    public void writeShort(short value) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(ByteBuffer.allocate(2).putShort(value).array());
    }

    public void writeLong(long value) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(ByteBuffer.allocate(8).putLong(value).array());
    }

    public void writeByte(byte value) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(value);
    }

    public void writeBoolean(boolean value) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(value ? 0x01 : 0x00);
    }

    public void writeDouble(double value) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(ByteBuffer.allocate(8).putDouble(value).array());
    }

    public void writeFloat(float value) throws IOException
    {
        this.ensureOutputStream();
        this.outputStream.write(ByteBuffer.allocate(4).putFloat(value).array());
    }

    @SuppressWarnings("ShiftOutOfRange")
    public void writePosition(BlockPosition blockPosition) throws IOException
    {
        this.ensureOutputStream();
        this.writeLong(((blockPosition.getX() & 0x3FFFFFFL) << 38) | ((blockPosition.getY() & 0xFFFL) << 26) | (blockPosition.getZ() & 0x3FFFFFFL));
    }

    public int available() throws IOException
    {
        this.ensureInputStream();
        return this.inputStream.available();
    }

    /**
     * Attempts to read a VarInt from {@code data} starting at {@code offset} without
     * consuming beyond {@code limit}. Returns {@code null} when the VarInt is not yet
     * fully present in the buffer (partial frame header), otherwise a two element array
     * {@code {value, bytesConsumed}}.
     */
    public static int[] tryReadVarInt(byte[] data, int offset, int limit) throws IOException
    {
        int out = 0;
        int bytes = 0;
        int index = offset;
        while (true)
        {
            if (index >= limit)
                return null;
            int in = data[index++] & 0xFF;
            out |= (in & 0x7F) << (bytes++ * 7);
            if (bytes > 5)
                throw new IOException("VarInt too big");
            if ((in & 0x80) != 0x80)
                break;
        }
        return new int[]{out, index - offset};
    }
}
