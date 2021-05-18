package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;

public class Main {
    private static File rf = new File("regions");
    private static DateFormat date = new SimpleDateFormat("dd MMM yyyy 'at' hh:mm:ss a zzz");
    private static long chunkCount = 0;
    public static Map<String, Long> blockCounter = new HashMap<String, Long>();
    public static Map<String, HashMap<Integer, Long>> heightCounter = new HashMap<String, HashMap<Integer, Long>>();

    public static void main(String[] args) {
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
        System.out.println(
                ("Completed in " + String.format("%.2f", (double) (System.currentTimeMillis() - firstStartTime) / 1000)
                        + "s (" + chunkCount + " chunks)"));
        long totalBlocks = 0L;
        for(String key : blockCounter.keySet()) {
            totalBlocks += blockCounter.get(key);
        }
        System.out.println("--------------------------------\n" + blockCounter.size() + " unique blocks\n" + totalBlocks
                + " blocks total");
        String output = "";
        for(String key : blockCounter.keySet()) {
            output += key + "," + blockCounter.get(key) + ","
                    + (((double) blockCounter.get(key) / (double) totalBlocks) * 100.0d) + "\n";
        }

        try {
            writeStringToFile(new File("totals.csv"), output);
        } catch(IOException e) {
            e.printStackTrace();
        }

        String data = "id,";
        for(int i = 0; i < 256; i++) {
            data += i + ",";
        }
        data += "\n";
        for(String key : heightCounter.keySet()) {
            data += key + ",";
            for(int i = 0; i < 256; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data += "0";
                } else {
                    data += heightCounter.get(key).get(i);
                }
                data += ",";
            }
            data += "\n";
        }

        try {
            writeStringToFile(new File("data.csv"), data);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static void processRegion(RegionFile r, String name, int x, int z) throws Exception {
        NBTTagList sections = CompressedStreamTools.read(r.getChunkDataInputStream(x, z)).getCompoundTag("Level")
                .getTagList("Sections", 10);
        analyzeChunk(x, z, sections);
    }

    private static void analyzeChunk(int chunkX, int chunkZ, NBTTagList sections) {
        String debug = "";
        for(int i = 0; i < sections.tagCount(); i++) {
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
                        int pos = y * 16 * 16 + z * 16 + x;
                        debug += String.format("{%d, %d} [(%d, %d) %d, %d (%d), %d] %s\n", chunkX, chunkZ, i, sectionY,
                                x, y, actualY, z, palette.getStringTagAt(blocks[pos]));
                    }
                }
            }
        }
        try {
            writeStringToFile(new File("chunk-" + chunkX + "." + chunkZ + ".txt"), debug);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static String formatRegionName(File f) {
        return f.getPath().split("region")[1].substring(4).replace(".mca", "");
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
}