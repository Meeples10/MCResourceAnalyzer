package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerIndev extends RegionAnalyzer {

    @Override
    public void validateInput(File world) {
        if(!world.exists()) {
            System.err.println("Error: File not found: " + world.getAbsolutePath());
            System.exit(1);
        }
    }

    @Override
    public void findChunks(File world) {
        // TODO
    }

    @Override
    public void analyze(File world) {
        try {
            processWorld(world);
        } catch(Exception e) {
            e.printStackTrace();
        }
        duration = System.currentTimeMillis() - getStartTime();
        Main.println(("Completed analysis in " + Main.millisToHMS(duration)));
    }

    private void processWorld(File worldFile) throws Exception {
        NBTTagCompound map = CompressedStreamTools.readCompressed(new FileInputStream(worldFile)).getCompoundTag("Map");
        int width = map.getInteger("Width");
        int height = map.getInteger("Height");
        byte[] blocks = map.getByteArray("Blocks");
        byte[] rawData = map.getByteArray("Data");
        byte[] data = new byte[rawData.length];
        for(int i = 0; i < rawData.length; i++) {
            data[i] = (byte) ((rawData[i] >> 4) & (byte) 0x0F);
        }
        analyzeWorld(blocks, data, width, height);
    }

    private void analyzeWorld(byte[] blocks, byte[] data, int width, int height) {
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

                    if(blockCounter.containsKey(blockName)) {
                        blockCounter.put(blockName, blockCounter.get(blockName) + 1L);
                    } else {
                        blockCounter.put(blockName, 1L);
                    }
                    if(!heightCounter.containsKey(blockName)) {
                        heightCounter.put(blockName, new ConcurrentHashMap<Integer, Long>());
                    }
                    if(heightCounter.get(blockName).containsKey(y)) {
                        heightCounter.get(blockName).put(y, heightCounter.get(blockName).get(y) + 1L);
                    } else {
                        heightCounter.get(blockName).put(y, 1L);
                    }
                }
            }
        }
    }
}
