package cn.starry.narcissus.functions.world.nbt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/** NBT TAG_Int_Array. */
public class IntArrayTag extends NamedTag
{
    public static final byte ID = 11;
    private int[] values;

    public IntArrayTag()
    {
        this.values = new int[0];
    }

    public IntArrayTag(int[] values)
    {
        this.values = values == null ? new int[0] : values.clone();
    }

    @Override
    public void read(InputStream inputStream, boolean readName) throws IOException
    {
        super.read(inputStream, readName);
        int length = this.readInt(inputStream);
        if (length < 0 || length > MAX_ARRAY_LENGTH)
            throw new IOException("NBT int array length out of bounds: " + length);
        this.values = new int[length];
        for (int i = 0; i < length; i++)
            this.values[i] = this.readInt(inputStream);
    }

    public int[] getValues()
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
        for (int value : this.values)
            this.writeInt(outputStream, value);
    }
}
