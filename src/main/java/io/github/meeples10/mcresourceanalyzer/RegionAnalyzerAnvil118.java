package io.github.meeples10.mcresourceanalyzer;

import java.io.DataInputStream;
import java.io.File;
import java.util.Date;
import java.util.HashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;

public class RegionAnalyzerAnvil118 extends RegionAnalyzer {

    @Override
    public void analyze(File regionDir) {
        if(!regionDir.exists()) {
            System.out.println("Error: No region directory found at " + regionDir.getAbsolutePath());
            System.exit(1);
        }
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
        duration = System.currentTimeMillis() - getStartTime();
        System.out.println(("Completed analysis in " + Main.millisToHMS(duration) + " (" + chunkCount + " chunks)"));
    }

    private void processRegion(RegionFile r, String name, int x, int z) throws Exception {
        DataInputStream chunkDataInputStream = r.getChunkDataInputStream(x, z);
        if(chunkDataInputStream == null) {
            // Skip malformed chunks
            return;
        }
        NBTTagList sections = CompressedStreamTools.read(r.getChunkDataInputStream(x, z)).getTagList("sections", 10);
        analyzeChunk(sections);
        chunkCount++;
    }

    private void analyzeChunk(NBTTagList sections) {
        int i = 0;
        for(; i < sections.tagCount(); i++) {
            NBTTagCompound tag = sections.getCompoundTagAt(i);

            NBTTagLongArray blockStatesTag = ((NBTTagLongArray) tag.getCompoundTag("block_states").getTag("data"));
            long[] blockStates = blockStatesTag == null ? new long[0] : blockStatesTag.get();
            NBTTagList palette = tag.getCompoundTag("block_states").getTagList("palette", 10);
            if(palette.hasNoTags()) continue;
            int bitLength = Main.bitLength(palette.tagCount() - 1);
            bitLength = bitLength < 4 ? 4 : bitLength;
            int[] blocks = Main.unstream(bitLength, 64, true, blockStates);
            if(blocks.length == 0) continue; // skip empty sections
            int sectionY = tag.getByte("Y");

            for(int y = 0; y < 16; y++) {
                for(int x = 0; x < 16; x++) {
                    for(int z = 0; z < 16; z++) {
                        int actualY = sectionY * 16 + y;
                        int blockIndex = y * 16 * 16 + z * 16 + x;
                        String block = palette.getStringTagAt(blocks[blockIndex]);
                        int startIndex = block.indexOf("Name:\"");
                        String blockName = block.substring(startIndex + 6, block.indexOf('"', startIndex + 6));
                        if(blockCounter.containsKey(blockName)) {
                            blockCounter.put(blockName, blockCounter.get(blockName) + 1L);
                        } else {
                            blockCounter.put(blockName, 1L);
                        }
                        if(!heightCounter.containsKey(blockName)) {
                            heightCounter.put(blockName, new HashMap<Integer, Long>());
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
        airHack(i, "minecraft:air");
    }
}
