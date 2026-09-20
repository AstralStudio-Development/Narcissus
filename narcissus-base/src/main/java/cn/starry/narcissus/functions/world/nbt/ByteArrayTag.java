package cn.starry.narcissus.functions.world.nbt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by Rigner on 30/08/16 for project Limbo.
 * All rights reserved.
 */
public class ByteArrayTag extends NamedTag
{
    public static final byte ID = 7;
    private byte[] bytes;

    public void read(InputStream inputStream, boolean readName) throws IOException
    {
        super.read(inputStream, readName);
        int length = this.readInt(inputStream);
        if (length < 0 || length > MAX_ARRAY_LENGTH)
            throw new IOException("NBT byte array length out of bounds: " + length);
        this.bytes = new byte[length];
        this.readByteArray(inputStream, this.bytes);
    }

    public byte[] getBytes()
    {
        return this.bytes;
    }

    public String toString()
    {
        return Arrays.toString(this.bytes);
    }

    public byte getId()
    {
        return 7;
    }

    @Override
    public void write(OutputStream outputStream, boolean writeName) throws IOException
    {
        super.write(outputStream, writeName);
        this.writeInt(outputStream, this.bytes.length);
        this.writeByteArray(outputStream, this.bytes);
    }
}
