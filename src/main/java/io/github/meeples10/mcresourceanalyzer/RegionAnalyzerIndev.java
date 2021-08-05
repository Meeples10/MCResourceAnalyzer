package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class RegionAnalyzerIndev extends RegionAnalyzer {

    @Override
    public void analyze(File ignore) {
        File world = new File("world.mclevel");
        if(!world.exists()) {
            System.out.println("Error: No world file found at " + world.getAbsolutePath());
            System.exit(1);
        }
        try {
            processWorld(world);
        } catch(Exception e) {
            e.printStackTrace();
        }
        long duration = System.currentTimeMillis() - getStartTime();
        System.out.println(("Completed analysis in " + Main.millisToHMS(duration)));
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
        String data = "";
        if(Main.saveStatistics) {
            data += "chunk-count=" + chunkCount + ",unique-blocks=" + blockCounter.size() + ",total-blocks="
                    + totalBlocks + ",duration-millis=" + duration + ",duration-readable=" + Main.millisToHMS(duration)
                    + "\n";
        }
        data += "id,";
        for(int i = 0; i < 128; i++) {
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
            for(int i = 0; i < 128; i++) {
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
            File out = new File("data.csv");
            Main.writeStringToFile(out, data);
            System.out.println("\nData written to " + out.getAbsolutePath());
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        if(Main.generateTable) {
            try {
                File out = new File("table.html");
                Main.writeStringToFile(out, generateTable((double) totalBlocks, totalExcludingAir));
                System.out.println("\nTable written to " + out.getAbsolutePath());
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
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
        chunkCount++;
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
