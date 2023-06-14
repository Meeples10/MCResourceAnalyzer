package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerAlpha extends RegionAnalyzer {
    final List<File> chunkFiles = new ArrayList<>();
    private Analysis a = new Analysis();

    @Override
    public void validateInput(File world) {
        if(!world.exists()) {
            System.err.println("Error: No world directory found at " + world.getAbsolutePath());
            System.exit(1);
        }
    }

    @Override
    public void findChunks(File world) {
        for(File f : world.listFiles(Main.DS_STORE_FILTER)) {
            if(!f.isDirectory()) continue;
            chunkFiles.addAll(traverseSubdirectories(f));
        }
        if(chunkFiles.size() == 0) {
            System.err.println("Error: World directory is empty");
            System.exit(1);
        }
        Main.println(chunkFiles.size() + " chunks found");
    }

    @Override
    public void analyze() {
        int i = 1;
        for(File f : chunkFiles) {
            System.out.print("Scanning chunks [" + i + "/" + chunkFiles.size() + "]\r");

            try {
                processChunk(f);
            } catch(Exception e) {
                e.printStackTrace();
            }
            i++;
        }
        blockCounter.putAll(a.blocks);
        heightCounter.putAll(a.heights);
    }

    private void processChunk(File chunkFile) throws Exception {
        NBTTagCompound chunk = CompressedStreamTools.readCompressed(new FileInputStream(chunkFile));
        NBTTagCompound level = chunk.getCompoundTag("Level");
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

                    if(a.blocks.containsKey(blockName)) {
                        a.blocks.put(blockName, a.blocks.get(blockName) + 1L);
                    } else {
                        a.blocks.put(blockName, 1L);
                    }
                    if(!a.heights.containsKey(blockName)) {
                        a.heights.put(blockName, new Hashtable<Integer, Long>());
                    }
                    if(a.heights.get(blockName).containsKey(y)) {
                        a.heights.get(blockName).put(y, a.heights.get(blockName).get(y) + 1L);
                    } else {
                        a.heights.get(blockName).put(y, 1L);
                    }
                }
            }
        }
    }

    private static List<File> traverseSubdirectories(File root) {
        List<File> files = new ArrayList<>();
        for(File f : root.listFiles()) {
            if(f.isDirectory()) {
                files.addAll(traverseSubdirectories(f));
            } else {
                files.add(f);
            }
        }
        return files;
    }
}
