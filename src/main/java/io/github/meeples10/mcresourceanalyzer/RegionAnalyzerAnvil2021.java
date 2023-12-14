package io.github.meeples10.mcresourceanalyzer;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;

public class RegionAnalyzerAnvil2021 extends RegionAnalyzer {

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
            Region r = new Region(f);
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
            threads.add(new AnalyzerThread(this, r) {
                public void process() {
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
        if(chunkDataInputStream == null) return null;
        return analyzeChunk(
                CompressedStreamTools.read(chunkDataInputStream).getCompoundTag("Level").getTagList("Sections", 10));
    }

    private Analysis analyzeChunk(NBTTagList sections) {
        Analysis a = new Analysis();
        int i = 0;
        for(; i < sections.tagCount(); i++) {
            NBTTagCompound section = sections.getCompoundTagAt(i);
            NBTTagList palette = section.getTagList("Palette", 10);
            if(palette.hasNoTags()) {
                airHack(a, i, "minecraft:air");
                continue;
            }
            NBTTagLongArray blockStatesTag = ((NBTTagLongArray) section.getTag("BlockStates"));
            int bitLength = Main.bitLength(palette.tagCount() - 1);
            int[] blocks = Main.unstream(bitLength < 4 ? 4 : bitLength, 64, true,
                    blockStatesTag == null ? new long[0] : blockStatesTag.get());
            if(blocks.length == 0) {
                airHack(a, i, "minecraft:air");
                continue;
            }
            int sectionY = section.getByte("Y");

            for(int y = 0; y < 16; y++) {
                for(int x = 0; x < 16; x++) {
                    for(int z = 0; z < 16; z++) {
                        int actualY = sectionY * 16 + y;
                        String blockState = palette.getStringTagAt(blocks[y * 16 * 16 + z * 16 + x]);
                        int startIndex = blockState.indexOf("Name:\"") + 6;
                        String blockName = blockState.substring(startIndex, blockState.indexOf('"', startIndex));
                        a.blocks.put(blockName, a.blocks.getOrDefault(blockName, 0L) + 1L);
                        if(a.heights.containsKey(blockName)) {
                            a.heights.get(blockName).put(actualY,
                                    a.heights.get(blockName).getOrDefault(actualY, 0L) + 1L);
                        } else {
                            a.heights.put(blockName, new Hashtable<Integer, Long>() {
                                {
                                    put(actualY, 1L);
                                }
                            });
                        }
                    }
                }
            }
        }
        return a;
    }
}
