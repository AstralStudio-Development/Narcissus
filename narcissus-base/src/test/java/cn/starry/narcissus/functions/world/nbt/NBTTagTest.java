package cn.starry.narcissus.functions.world.nbt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NBTTagTest
{
    @Test
    void writesNegativeNumericTagsUsingBigEndianTwoComplement() throws Exception
    {
        IntTag intTag = new IntTag(-1);
        intTag.setName("int");
        LongTag longTag = new LongTag(-1L);
        longTag.setName("long");
        ShortTag shortTag = new ShortTag((short) -2);
        shortTag.setName("short");

        assertEquals(-1, roundTrip(intTag).toIntTag().getValue());
        assertEquals(-1L, roundTrip(longTag).toLongTag().getValue());
        assertEquals((short) -2, roundTrip(shortTag).toShortTag().getValue());
    }

    @Test
    void readsAndWritesIntAndLongArrays() throws Exception
    {
        IntArrayTag ints = new IntArrayTag(new int[]{-1, 0, 123456});
        ints.setName("ints");
        LongArrayTag longs = new LongArrayTag(new long[]{-1L, 0L, Long.MAX_VALUE});
        longs.setName("longs");

        assertEquals(-1, roundTrip(ints).toIntArrayTag().getValues()[0]);
        assertEquals(123456, roundTrip(ints).toIntArrayTag().getValues()[2]);
        assertEquals(-1L, roundTrip(longs).toLongArrayTag().getValues()[0]);
        assertEquals(Long.MAX_VALUE, roundTrip(longs).toLongArrayTag().getValues()[2]);
    }

    private static NBTTag roundTrip(NBTTag tag) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NBTTag.writeTag(bytes, tag, true);
        return NBTTag.readTag(new ByteArrayInputStream(bytes.toByteArray()));
    }
}
