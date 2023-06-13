package io.github.meeples10.mcresourceanalyzer;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;

public class RegionAnalyzerAnvil118 extends RegionAnalyzer {

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
    public void findChunks(File regionDir) {
        for(File f : regionDir.listFiles(Main.DS_STORE_FILTER)) {
            Region r = new Region(f, Main.formatRegionName(regionDir, f));
            for(int x = 0; x < 32; x++) {
                for(int z = 0; z < 32; z++) {
                    if(r.file.hasChunk(x, z)) r.addChunk(x, z);
                }
            }
            regions.add(r);
        }
    }

    @Override
    public void analyze() {
        for(Region r : regions) {
            threads.add(new AnalyzerThread(r) {
                @Override
                public void run() {
                    for(Chunk c : r.chunks) {
                        try {
                            Analysis a = processRegion(r.file, c.x(), c.z());
                            if(a != null) analyses.add(a);
                            updateProgress();
                        } catch(IOException e) {
                            e.printStackTrace();
                            halt();
                        }
                    }
                }
            });
        }
    }

    private Analysis processRegion(RegionFile r, int x, int z) throws IOException {
        DataInputStream chunkDataInputStream = r.getChunkDataInputStream(x, z);
        if(chunkDataInputStream == null) {
            // Skip malformed chunks
            return null;
        }
        NBTTagList sections = CompressedStreamTools.read(r.getChunkDataInputStream(x, z)).getTagList("sections", 10);
        return analyzeChunk(sections);
    }

    private Analysis analyzeChunk(NBTTagList sections) {
        Analysis a = new Analysis();
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
                        if(a.blocks.containsKey(blockName)) {
                            a.blocks.put(blockName, a.blocks.get(blockName) + 1L);
                        } else {
                            a.blocks.put(blockName, 1L);
                        }
                        if(!a.heights.containsKey(blockName)) {
                            a.heights.put(blockName, new HashMap<Integer, Long>());
                        }
                        if(a.heights.get(blockName).containsKey(actualY)) {
                            a.heights.get(blockName).put(actualY, a.heights.get(blockName).get(actualY) + 1L);
                        } else {
                            a.heights.get(blockName).put(actualY, 1L);
                        }
                    }
                }
            }
        }
        airHack(a, i, "minecraft:air");
        return a;
    }
}
