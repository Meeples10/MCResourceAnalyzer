package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NBTTagByteArray extends NBTBase {
    /** The byte array stored in the tag. */
    private byte[] data;

    NBTTagByteArray() {}

    public NBTTagByteArray(byte[] data) {
        this.data = data;
    }

    public NBTTagByteArray(List<Byte> bytes) {
        this(fromList(bytes));
    }

    private static byte[] fromList(List<Byte> bytes) {
        byte[] abyte = new byte[bytes.size()];

        for(int i = 0; i < bytes.size(); ++i) {
            Byte b = bytes.get(i);
            abyte[i] = b == null ? 0 : b.byteValue();
        }

        return abyte;
    }

    /**
     * Write the actual data contents of the tag, implemented in NBT extension classes
     */
    void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);
        output.write(this.data);
    }

    void read(DataInput input, int depth, NBTSizeTracker sizeTracker) throws IOException {
        sizeTracker.read(192L);
        int i = input.readInt();
        sizeTracker.read((long) (8 * i));
        this.data = new byte[i];
        input.readFully(this.data);
    }

    /**
     * Gets the type byte for the tag.
     */
    public byte getId() {
        return 7;
    }

    public String toString() {
        StringBuilder stringbuilder = new StringBuilder("[B;");

        for(int i = 0; i < this.data.length; ++i) {
            if(i != 0) {
                stringbuilder.append(',');
            }

            stringbuilder.append((int) this.data[i]).append('B');
        }

        return stringbuilder.append(']').toString();
    }

    /**
     * Creates a clone of the tag.
     */
    public NBTBase copy() {
        byte[] abyte = new byte[this.data.length];
        System.arraycopy(this.data, 0, abyte, 0, this.data.length);
        return new NBTTagByteArray(abyte);
    }

    public boolean equals(Object o) {
        return super.equals(o) && Arrays.equals(this.data, ((NBTTagByteArray) o).data);
    }

    public int hashCode() {
        return super.hashCode() ^ Arrays.hashCode(this.data);
    }

    public byte[] getByteArray() {
        return this.data;
    }
}
