package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NBTTagFloat extends NBTPrimitive {
    /** The float value for the tag. */
    private float data;

    NBTTagFloat() {}

    public NBTTagFloat(float data) {
        this.data = data;
    }

    /**
     * Write the actual data contents of the tag, implemented in NBT extension classes
     */
    void write(DataOutput output) throws IOException {
        output.writeFloat(this.data);
    }

    void read(DataInput input, int depth, NBTSizeTracker sizeTracker) throws IOException {
        sizeTracker.read(96L);
        this.data = input.readFloat();
    }

    /**
     * Gets the type byte for the tag.
     */
    public byte getId() {
        return 5;
    }

    public String toString() {
        return this.data + "f";
    }

    /**
     * Creates a clone of the tag.
     */
    public NBTTagFloat copy() {
        return new NBTTagFloat(this.data);
    }

    public boolean equals(Object o) {
        return super.equals(o) && this.data == ((NBTTagFloat) o).data;
    }

    public int hashCode() {
        return super.hashCode() ^ Float.floatToIntBits(this.data);
    }

    public long getLong() {
        return (long) this.data;
    }

    public int getInt() {
        return (int) Math.floor(this.data);
    }

    public short getShort() {
        return (short) ((int) Math.floor(this.data) & 65535);
    }

    public byte getByte() {
        return (byte) ((int) Math.floor(this.data) & 255);
    }

    public double getDouble() {
        return (double) this.data;
    }

    public float getFloat() {
        return this.data;
    }
}
