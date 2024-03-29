package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerIndev extends RegionAnalyzer {
    private byte[] blocks = null;
    private byte[] data = null;
    private int width = 0;
    private int height = 0;

    @Override
    public void validateInput(File world) {
        if(!world.exists()) {
            System.err.println("Error: File not found: " + world.getAbsolutePath());
            System.exit(1);
        }
    }

    @Override
    public void findChunks(File world) {
        NBTTagCompound map = null;
        try {
            map = CompressedStreamTools.readCompressed(new FileInputStream(world)).getCompoundTag("Map");
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        width = map.getInteger("Width");
        height = map.getInteger("Height");
        blocks = map.getByteArray("Blocks");
        byte[] rawData = map.getByteArray("Data");
        data = new byte[rawData.length];
        for(int i = 0; i < rawData.length; i++) {
            data[i] = (byte) ((rawData[i] >> 4) & (byte) 0x0F);
        }
    }

    @Override
    public void analyze() {
        Analysis a = analyzeWorld(blocks, data, width, height);
        duration = System.currentTimeMillis() - getStartTime();
        Main.printf("Completed analysis in %d\n", Main.millisToHMS(duration));
        blockCounter.putAll(a.blocks);
        heightCounter.putAll(a.heights);
    }

    private Analysis analyzeWorld(byte[] blocks, byte[] data, int width, int height) {
        Analysis a = new Analysis();
        for(int y = 0; y < 128; y++) {
            for(int x = 0; x < 16; x++) {
                for(int z = 0; z < 16; z++) {
                    int index = (y * height + z) * width + x;
                    byte blockID = blocks[index];
                    byte blockData = data[index];
                    String blockName;
                    if(mergeStates(blockID)) {
                        blockName = Byte.toString(blockID);
                    } else {
                        blockName = blockData == 0 ? Byte.toString(blockID)
                                : Byte.toString(blockID) + ":" + Byte.toString(blockData);
                    }

                    a.blocks.put(blockName, a.blocks.getOrDefault(blockName, 0L) + 1L);
                    if(a.heights.containsKey(blockName)) {
                        a.heights.get(blockName).put(y, a.heights.get(blockName).getOrDefault(y, 0L) + 1L);
                    } else {
                        final int yf = y;
                        a.heights.put(blockName, new Hashtable<Integer, Long>() {
                            {
                                put(yf, 1L);
                            }
                        });
                    }
                }
            }
        }
        return a;
    }
}
