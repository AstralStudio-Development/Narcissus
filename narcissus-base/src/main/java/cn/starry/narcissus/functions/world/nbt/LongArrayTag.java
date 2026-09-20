package cn.starry.narcissus.functions.world.nbt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/** NBT TAG_Long_Array. */
public class LongArrayTag extends NamedTag
{
    public static final byte ID = 12;
    private long[] values;

    public LongArrayTag()
    {
        this.values = new long[0];
    }

    public LongArrayTag(long[] values)
    {
        this.values = values == null ? new long[0] : values.clone();
    }

    @Override
    public void read(InputStream inputStream, boolean readName) throws IOException
    {
        super.read(inputStream, readName);
        int length = this.readInt(inputStream);
        if (length < 0 || length > MAX_ARRAY_LENGTH)
            throw new IOException("NBT long array length out of bounds: " + length);
        this.values = new long[length];
        for (int i = 0; i < length; i++)
            this.values[i] = this.readLong(inputStream);
    }

    public long[] getValues()
    {
        return this.values.clone();
    }

    @Override
    public byte getId()
    {
        return ID;
    }

    @Override
    public String toString()
    {
        return Arrays.toString(this.values);
    }

    @Override
    public void write(OutputStream outputStream, boolean writeName) throws IOException
    {
        super.write(outputStream, writeName);
        this.writeInt(outputStream, this.values.length);
        for (long value : this.values)
            this.writeLong(outputStream, value);
    }
}
