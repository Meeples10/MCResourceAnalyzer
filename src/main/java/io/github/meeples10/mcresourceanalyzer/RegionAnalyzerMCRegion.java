package io.github.meeples10.mcresourceanalyzer;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerMCRegion extends RegionAnalyzer {

    @Override
    public void validateInput(File regionDir) {
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
        return analyzeChunk(blocks, data);
    }

    private Analysis analyzeChunk(byte[] blocks, byte[] data) {
        Analysis a = new Analysis();
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

                    if(a.blocks.containsKey(blockName)) {
                        a.blocks.put(blockName, a.blocks.get(blockName) + 1L);
                    } else {
                        a.blocks.put(blockName, 1L);
                    }
                    if(!a.heights.containsKey(blockName)) {
                        a.heights.put(blockName, new HashMap<Integer, Long>());
                    }
                    if(a.heights.get(blockName).containsKey(y)) {
                        a.heights.get(blockName).put(y, a.heights.get(blockName).get(y) + 1L);
                    } else {
                        a.heights.get(blockName).put(y, 1L);
                    }
                }
            }
        }
        return a;
    }
}
