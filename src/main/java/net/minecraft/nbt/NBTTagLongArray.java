package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NBTTagLongArray extends NBTBase {
    private long[] internalArray;

    NBTTagLongArray() {}

    public NBTTagLongArray(long[] longs) {
        this.internalArray = longs;
    }

    public NBTTagLongArray(List<Long> longs) {
        this(fromList(longs));
    }

    private static long[] fromList(List<Long> longs) {
        long[] along = new long[longs.size()];

        for(int i = 0; i < longs.size(); ++i) {
            Long l = longs.get(i);
            along[i] = l == null ? 0L : l.longValue();
        }

        return along;
    }

    /**
     * Write the actual data contents of the tag, implemented in NBT extension classes
     */
    void write(DataOutput output) throws IOException {
        output.writeInt(this.internalArray.length);

        for(long i : this.internalArray) {
            output.writeLong(i);
        }
    }

    void read(DataInput input, int depth, NBTSizeTracker sizeTracker) throws IOException {
        sizeTracker.read(192L);
        int i = input.readInt();
        sizeTracker.read((long) (64 * i));
        this.internalArray = new long[i];

        for(int j = 0; j < i; ++j) {
            this.internalArray[j] = input.readLong();
        }
    }

    /**
     * Gets the type byte for the tag.
     */
    public byte getId() {
        return 12;
    }

    public String toString() {
        StringBuilder stringbuilder = new StringBuilder("[L;");

        for(int i = 0; i < this.internalArray.length; ++i) {
            if(i != 0) {
                stringbuilder.append(',');
            }

            stringbuilder.append(this.internalArray[i]).append('L');
        }

        return stringbuilder.append(']').toString();
    }

    /**
     * Creates a clone of the tag.
     */
    public NBTTagLongArray copy() {
        long[] along = new long[this.internalArray.length];
        System.arraycopy(this.internalArray, 0, along, 0, this.internalArray.length);
        return new NBTTagLongArray(along);
    }

    public boolean equals(Object o) {
        return super.equals(o)
                && Arrays.equals(this.internalArray, ((NBTTagLongArray) o).internalArray);
    }

    public int hashCode() {
        return super.hashCode() ^ Arrays.hashCode(this.internalArray);
    }

    public long[] get() {
        return internalArray;
    }
}
