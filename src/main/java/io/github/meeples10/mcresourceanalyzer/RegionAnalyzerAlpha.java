package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerAlpha extends RegionAnalyzer {

    @Override
    public void validateInput(File world) {
        if(!world.exists()) {
            System.err.println("Error: No world directory found at " + world.getAbsolutePath());
            System.exit(1);
        }
    }

    @Override
    public void findChunks(File input) {
        // TODO
    }

    @Override
    public void analyze(File world) {
        List<File> chunkFiles = new ArrayList<>();
        for(File f : world.listFiles(Main.DS_STORE_FILTER)) {
            if(!f.isDirectory()) continue;
            chunkFiles.addAll(traverseSubdirectories(f));
        }
        if(chunkFiles.size() == 0) {
            System.err.println("Error: World directory is empty");
            System.exit(1);
        }
        Main.println(chunkFiles.size() + " chunks found");
        int cnum = 1;
        for(File f : chunkFiles) {
            long startTime = System.currentTimeMillis();
            String name = f.getName();
            System.out.print("Scanning chunk " + name + " [" + cnum + "/" + chunkFiles.size() + "]... ");

            try {
                processChunk(f);
            } catch(Exception e) {
                e.printStackTrace();
            }

            Main.println(
                    "Done (" + String.format("%.2f", (double) (System.currentTimeMillis() - startTime) / 1000) + "s)");
            cnum++;
        }
        duration = System.currentTimeMillis() - getStartTime();
        Main.println(("Completed analysis in " + Main.millisToHMS(duration) + " (" + chunkCount + " chunks)"));
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
