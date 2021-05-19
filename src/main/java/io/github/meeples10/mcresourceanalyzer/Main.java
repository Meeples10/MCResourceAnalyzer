package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;

public class Main {
    private static File rf = new File("region");
    private static DateFormat date = new SimpleDateFormat("dd MMM yyyy 'at' hh:mm:ss a zzz");
    private static long chunkCount = 0;
    public static Map<String, Long> blockCounter = new HashMap<String, Long>();
    public static Map<String, HashMap<Integer, Long>> heightCounter = new HashMap<String, HashMap<Integer, Long>>();
    private static DecimalFormat decimalFormat = new DecimalFormat("0.##########");
    private static boolean saveStatistics = false;
    private static boolean allowHack = true;
    private static boolean generateTable = false;

    public static void main(String[] args) {
        decimalFormat.setMaximumFractionDigits(10);
        for(String arg : args) {
            if(arg.equalsIgnoreCase("statistics")) {
                saveStatistics = true;
            } else if(arg.equalsIgnoreCase("no-hack")) {
                allowHack = false;
            } else if(arg.equalsIgnoreCase("table")) {
                generateTable = true;
            }
        }
        System.out.println("Save statistics: " + saveStatistics + "\nAllow empty section hack: " + allowHack
                + "\nGenerate HTML table: " + generateTable + "\n--------------------------------");
        long firstStartTime = System.currentTimeMillis();
        int totalRegions = rf.listFiles().length;
        System.out.println(totalRegions + " regions found");
        int rnum = 1;
        for(File f : rf.listFiles()) {
            long startTime = System.currentTimeMillis();
            String name = formatRegionName(f);
            RegionFile r = new RegionFile(f);
            System.out.print("Scanning region " + name + " [" + rnum + "/" + totalRegions + "] (modified "
                    + date.format(new Date(r.lastModified())) + ")... ");
            for(int x = 0; x < 32; x++) {
                for(int z = 0; z < 32; z++) {
                    if(r.hasChunk(x, z)) {
                        chunkCount++;
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
        long duration = System.currentTimeMillis() - firstStartTime;
        System.out.println(("Completed analysis in " + millisToHMS(duration) + " (" + chunkCount + " chunks)"));
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

        double totalExcludingAir = (double) (totalBlocks - blockCounter.get("minecraft:air")
                - blockCounter.get("minecraft:cave_air"));
        System.out.print("Generating CSV... ");
        String data = "";
        if(saveStatistics) {
            data += "chunk-count=" + chunkCount + ",unique-blocks=" + blockCounter.size() + ",total-blocks="
                    + totalBlocks + ",duration-millis=" + duration + ",duration-readable=" + millisToHMS(duration)
                    + "\n";
        }
        data += "id,";
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
            data += key + ",";
            for(int i = 0; i < 256; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data += "0,";
                } else {
                    data += heightCounter.get(key).get(i) + ",";
                }
            }
            data += blockCounter.get(key) + ","
                    + decimalFormat.format(((double) blockCounter.get(key) / (double) totalBlocks) * 100.0d);
            if(key.equals("minecraft:air") || key.equals("minecraft:cave_air")) {
                data += ",N/A";
            } else {
                data += "," + decimalFormat.format(((double) blockCounter.get(key) / totalExcludingAir) * 100.0d);
            }
            data += "\n";
        }
        try {
            File out = new File("data.csv");
            writeStringToFile(out, data);
            System.out.println("\nData written to " + out.getAbsolutePath());
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        if(generateTable) {
            try {
                File out = new File("table.html");
                writeStringToFile(out, generateTable((double) totalBlocks, totalExcludingAir));
                System.out.println("\nTable written to " + out.getAbsolutePath());
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        System.out.println("Completed after " + millisToHMS(System.currentTimeMillis() - firstStartTime));
    }

    private static void processRegion(RegionFile r, String name, int x, int z) throws Exception {
        NBTTagList sections = CompressedStreamTools.read(r.getChunkDataInputStream(x, z)).getCompoundTag("Level")
                .getTagList("Sections", 10);
        analyzeChunk(x, z, sections);
    }

    private static void analyzeChunk(int chunkX, int chunkZ, NBTTagList sections) {
        int i = 0;
        for(; i < sections.tagCount(); i++) {
            NBTTagCompound tag = sections.getCompoundTagAt(i);

            NBTTagLongArray blockStatesTag = ((NBTTagLongArray) tag.getTag("BlockStates"));
            long[] blockStates = blockStatesTag == null ? new long[0] : blockStatesTag.get();
            NBTTagList palette = tag.getTagList("Palette", 10);
            if(palette.hasNoTags()) continue;
            int bitLength = bitLength(palette.tagCount() - 1);
            bitLength = bitLength < 4 ? 4 : bitLength;
            int[] blocks = unstream(bitLength, 64, true, blockStates);
            int sectionY = tag.getByte("Y");

            for(int y = 0; y < 16; y++) {
                for(int x = 0; x < 16; x++) {
                    for(int z = 0; z < 16; z++) {
                        int actualY = sectionY * 16 + y;
                        String block = palette.getStringTagAt(blocks[y * 16 * 16 + z * 16 + x]);
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
        // THIS IS A HACK TO ACCOUNT FOR NONEXISTENT SECTIONS AT HIGH Y VALUES
        if(allowHack && i < 15) {
            for(; i < 16; i++) {
                blockCounter.put("minecraft:air", blockCounter.get("minecraft:air") + 4096L);
                for(int y = i * 16; y < i * 16 + 16; y++) {
                    if(heightCounter.get("minecraft:air").containsKey(y)) {
                        heightCounter.get("minecraft:air").put(y, heightCounter.get("minecraft:air").get(y) + 256L);
                    } else {
                        heightCounter.get("minecraft:air").put(y, 256L);
                    }
                }
            }
        }
    }

    private static String formatRegionName(File f) {
        return f.getPath().split("region")[1].substring(1);
    }

    private static int[] unstream(int bitsPerValue, int wordSize, boolean slack, long[] data) {
        // in: bits per value, word size, ignore spare bits, data
        // out: decoded array
        List<Integer> out = new ArrayList<Integer>();
        if(slack) {
            wordSize = (int) Math.floor(wordSize / bitsPerValue) * bitsPerValue;
        }
        int bl = 0;
        int v = 0;
        for(int i = 0; i < data.length; i++) {
            for(int n = 0; n < wordSize; n++) {
                int bit = (int) ((data[i] >> n) & 0x01);
                // v = (v << 1) | bit;
                v = (bit << bl) | v;
                bl++;
                if(bl >= bitsPerValue) {
                    out.add(v);
                    v = 0;
                    bl = 0;
                }
            }
        }
        int[] array = new int[out.size()];
        for(int i = 0; i < out.size(); i++) {
            array[i] = out.get(i);
        }
        return array;
    }

    private static int bitLength(int i) {
        return (int) (Math.log(i) / Math.log(2) + 1);
    }

    /**
     * @param file the output file
     * @param data the string to write to the file
     */
    public static void writeStringToFile(File file, String data) throws IOException {
        FileWriter out = null;
        try {
            out = new FileWriter(file);
            out.write(data);
        } catch(IOException e) {
            throw new IOException(e);
        } finally {
            if(out != null) try {
                out.close();
            } catch(IOException ignore) {}
        }
    }

    private static String millisToHMS(long millis) {
        return String.format("%02d:%02d:%02d.%03d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1), millis % 1000);
    }

    private static String generateTable(double totalBlocks, double totalExcludingAir) {
        String data = "<table>\n";
        data += "<tr><th>id</th><th>";
        for(int i = 0; i < 256; i++) {
            data += i + "</th><th>";
        }
        data += "total</th><th>percent_of_total</th><th>percent_excluding_air</th></tr>\n<tr>";
        int digits = String.valueOf(blockCounter.size()).length();
        String completionFormat = "[%0" + digits + "d/%0" + digits + "d]";
        int keyIndex = 0;
        for(String key : heightCounter.keySet()) {
            keyIndex += 1;
            System.out.print("\rGenerating table... " + String.format(completionFormat, keyIndex, blockCounter.size()));
            data += "<td>" + key + "</td>";
            for(int i = 0; i < 256; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data += "<td>0</td>";
                } else {
                    data += "<td>" + heightCounter.get(key).get(i) + "</td>";
                }
            }
            data += "<td>" + blockCounter.get(key) + "</td><td>"
                    + decimalFormat.format(((double) blockCounter.get(key) / totalBlocks) * 100.0d) + "</td>";
            if(key.equals("minecraft:air") || key.equals("minecraft:cave_air")) {
                data += "<td>N/A</td>";
            } else {
                data += "<td>" + decimalFormat.format(((double) blockCounter.get(key) / totalExcludingAir) * 100.0d)
                        + "</td>";
            }
            data += "</tr>\n<tr>";
        }
        return data.substring(0, data.length() - 4) + "</table>";
    }
}