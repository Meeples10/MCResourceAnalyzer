package io.github.meeples10.mcresourceanalyzer;

import java.io.DataInputStream;
import java.io.File;
import java.util.Date;
import java.util.HashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerMCRegion extends RegionAnalyzer {

    @Override
    public void analyze(File regionDir) {
        int totalRegions = regionDir.listFiles().length;
        if(totalRegions == 0) {
            System.out.println("Error: Region directory is empty");
            System.exit(1);
        }
        System.out.println(totalRegions + " regions found");
        int rnum = 1;
        for(File f : regionDir.listFiles()) {
            long startTime = System.currentTimeMillis();
            String name = Main.formatRegionName(regionDir, f);
            RegionFile r = new RegionFile(f);
            System.out.print("Scanning region " + name + " [" + rnum + "/" + totalRegions + "] (modified "
                    + Main.DATE_FORMAT.format(new Date(r.lastModified())) + ")... ");
            for(int x = 0; x < 32; x++) {
                for(int z = 0; z < 32; z++) {
                    if(r.hasChunk(x, z)) {
                        try {
                            processRegion(r, name, x, z);
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            System.out.println(
                    "Done (" + String.format("%.2f", (double) (System.currentTimeMillis() - startTime) / 1000) + "s)");
            rnum++;
        }
        long duration = System.currentTimeMillis() - getStartTime();
        System.out.println(("Completed analysis in " + Main.millisToHMS(duration) + " (" + chunkCount + " chunks)"));
    }

    private void processRegion(RegionFile r, String name, int x, int z) throws Exception {
        DataInputStream chunkDataInputStream = r.getChunkDataInputStream(x, z);
        if(chunkDataInputStream == null) {
            // Skip malformed chunks
            return;
        }
        NBTTagCompound level = CompressedStreamTools.read(r.getChunkDataInputStream(x, z)).getCompoundTag("Level");
        byte[] blocks = level.getByteArray("Blocks");
        byte[] rawData = level.getByteArray("Data");
        byte[] data = new byte[rawData.length * 2];
        int j = 0;
        for(int i = 0; i < rawData.length; i++) {
            byte a = (byte) ((rawData[i] >> 4) & (byte) 0x0F);
            byte b = (byte) (rawData[i] & 0x0F);
            data[j] = b;
            data[j + 1] = a;
            j += 2;
        }
        analyzeChunk(blocks, data);
        chunkCount++;
    }

    private void analyzeChunk(byte[] blocks, byte[] data) {
        for(int y = 0; y < 128; y++) {
            for(int x = 0; x < 16; x++) {
                for(int z = 0; z < 16; z++) {
                    int index = y + (z * 128 + (x * 128 * 16));
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
                        heightCounter.put(blockName, new HashMap<Integer, Long>());
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
