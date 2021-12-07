package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerAlpha extends RegionAnalyzer {

    @Override
    public void analyze(File world) {
        List<File> chunkFiles = new ArrayList<>();
        for(File f : world.listFiles()) {
            if(!f.isDirectory()) continue;
            chunkFiles.addAll(traverseSubdirectories(f));
        }

        if(chunkFiles.size() == 0) {
            System.out.println("Error: World directory is empty");
            System.exit(1);
        }
        System.out.println(chunkFiles.size() + " chunks found");
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

            System.out.println(
                    "Done (" + String.format("%.2f", (double) (System.currentTimeMillis() - startTime) / 1000) + "s)");
            cnum++;
        }
        long duration = System.currentTimeMillis() - getStartTime();
        System.out.println(("Completed analysis in " + Main.millisToHMS(duration) + " (" + chunkCount + " chunks)"));
        long totalBlocks = 0L;
        for(String key : blockCounter.keySet()) {
            totalBlocks += blockCounter.get(key);
        }
        System.out.println("--------------------------------\n" + blockCounter.size() + " unique blocks\n" + totalBlocks
                + " blocks total\n--------------------------------");

        System.out.print("Sorting data... ");
        heightCounter = heightCounter.entrySet().stream().sorted(Map.Entry.comparingByKey(new Comparator<String>() {
            @Override
            public int compare(String arg0, String arg1) {
                return Long.compare(blockCounter.get(arg1), blockCounter.get(arg0));
            }
        })).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        System.out.println("Done");

        double totalExcludingAir = (double) (totalBlocks - (blockCounter.containsKey("0") ? blockCounter.get("0") : 0));
        System.out.print("Generating CSV... ");
        String data = "id,";
        for(int i = 0; i < 256; i++) {
            data += i + ",";
        }
        data += "total,percent_of_total,percent_excluding_air\n";
        int digits = String.valueOf(blockCounter.size()).length();
        String completionFormat = "[%0" + digits + "d/%0" + digits + "d]";
        int keyIndex = 0;
        for(String key : heightCounter.keySet()) {
            keyIndex += 1;
            System.out.print("\rGenerating CSV... " + String.format(completionFormat, keyIndex, blockCounter.size()));
            data += (Main.modernizeIDs ? Main.getStringID(key) : key) + ",";
            for(int i = 0; i < 256; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data += "0,";
                } else {
                    data += heightCounter.get(key).get(i) + ",";
                }
            }
            data += blockCounter.get(key) + ","
                    + Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / (double) totalBlocks) * 100.0d);
            if(key.equals("0")) {
                data += ",N/A";
            } else {
                data += "," + Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / totalExcludingAir) * 100.0d);
            }
            data += "\n";
        }
        try {
            File out = new File(Main.getOutputPrefix() + ".csv");
            Main.writeStringToFile(out, data);
            System.out.println("\nData written to " + out.getAbsolutePath());
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        if(Main.generateTable) {
            try {
                File out = new File(Main.getOutputPrefix() + "_table.html");
                Main.writeStringToFile(out, generateTable((double) totalBlocks, totalExcludingAir));
                System.out.println("\nTable written to " + out.getAbsolutePath());
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        if(Main.saveStatistics) {
            try {
                Main.writeStringToFile(new File(Main.getOutputPrefix() + "_stats.txt"),
                        "chunk-count=" + chunkCount + "\nunique-blocks=" + blockCounter.size() + "\ntotal-blocks="
                                + totalBlocks + "\nduration-millis=" + duration + "\nduration-readable="
                                + Main.millisToHMS(duration));
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
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
