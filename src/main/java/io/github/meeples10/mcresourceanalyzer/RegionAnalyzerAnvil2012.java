package io.github.meeples10.mcresourceanalyzer;

import java.io.DataInputStream;
import java.io.File;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class RegionAnalyzerAnvil2012 extends RegionAnalyzer {

    @Override
    public void validateInput(File regionDir) {
        if(!regionDir.exists()) {
            System.err.println("Error: No region directory found at " + regionDir.getAbsolutePath());
            System.exit(1);
        }
        if(regionDir.listFiles(Main.DS_STORE_FILTER).length == 0) {
            System.err.println("Error: Region directory is empty");
            System.exit(1);
        }
    }

    @Override
    public void findChunks(File input) {
        // TODO
    }

    @Override
    public void analyze(File regionDir) {
        int totalRegions = regionDir.listFiles(Main.DS_STORE_FILTER).length;
        Main.println(totalRegions + " regions found");
        int rnum = 1;
        for(File f : regionDir.listFiles(Main.DS_STORE_FILTER)) {
            long startTime = System.currentTimeMillis();
            String name = Main.formatRegionName(regionDir, f);
            RegionFile r = new RegionFile(f);
            Main.print("Scanning region " + name + " [" + rnum + "/" + totalRegions + "] (modified "
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
            Main.println(
                    "Done (" + String.format("%.2f", (double) (System.currentTimeMillis() - startTime) / 1000) + "s)");
            rnum++;
        }
        duration = System.currentTimeMillis() - getStartTime();
        Main.println(("Completed analysis in " + Main.millisToHMS(duration) + " (" + chunkCount + " chunks)"));
    }

    private void processRegion(RegionFile r, String name, int x, int z) throws Exception {
        DataInputStream chunkDataInputStream = r.getChunkDataInputStream(x, z);
        if(chunkDataInputStream == null) {
            // Skip malformed chunks
            return;
        }
        NBTTagList sections = CompressedStreamTools.read(r.getChunkDataInputStream(x, z)).getCompoundTag("Level")
                .getTagList("Sections", 10);
        analyzeChunk(sections);
    }

    private void analyzeChunk(NBTTagList sections) {
        int i = 0;
        for(; i < sections.tagCount(); i++) {
            NBTTagCompound tag = sections.getCompoundTagAt(i);
            int sectionY = tag.getByte("Y");
            byte[] blocks = tag.getByteArray("Blocks");
            byte[] rawData = tag.getByteArray("Data");
            byte[] data = new byte[rawData.length * 2];
            int j = 0;
            for(int k = 0; k < rawData.length; k++) {
                byte a = (byte) ((rawData[k] >> 4) & (byte) 0x0F);
                byte b = (byte) (rawData[k] & 0x0F);
                data[j] = b;
                data[j + 1] = a;
                j += 2;
            }

            for(int y = 0; y < 16; y++) {
                for(int x = 0; x < 16; x++) {
                    for(int z = 0; z < 16; z++) {
                        int actualY = sectionY * 16 + y;
                        int blockIndex = y * 16 * 16 + z * 16 + x;

                        int blockID = blocks[blockIndex] & 0xFF;
                        byte blockData = data[blockIndex];
                        String blockName;
                        if(mergeStates(blockID)) {
                            blockName = Integer.toString(blockID);
                        } else {
                            blockName = blockData == 0 ? Integer.toString(blockID)
                                    : Integer.toString(blockID) + ":" + Byte.toString(blockData);
                        }
                        if(blockCounter.containsKey(blockName)) {
                            blockCounter.put(blockName, blockCounter.get(blockName) + 1L);
                        } else {
                            blockCounter.put(blockName, 1L);
                        }
                        if(!heightCounter.containsKey(blockName)) {
                            heightCounter.put(blockName, new ConcurrentHashMap<Integer, Long>());
                        }
                        if(heightCounter.get(blockName).containsKey(actualY)) {
                            heightCounter.get(blockName).put(actualY, heightCounter.get(blockName).get(actualY) + 1L);
                        } else {
                            heightCounter.get(blockName).put(actualY, 1L);
                        }
                    }
                }
            }
        }
        airHack(i, "0");
    }
}
