package io.github.meeples10.mcresourceanalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

public class Main {
    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy 'at' hh:mm:ss a zzz");
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##########");
    public static final Map<String, String> BLOCK_NAMES = new HashMap<>();
    public static final List<Integer> BLOCKS_TO_MERGE = new ArrayList<>();
    static boolean saveStatistics = false;
    static boolean allowHack = true;
    static boolean generateTable = false;
    static boolean versionSelect = false;
    static boolean versionOverride = false;
    static boolean modernizeIDs = false;
    static boolean inputOverride = false;

    public static void main(String[] args) {
        DECIMAL_FORMAT.setMaximumFractionDigits(10);
        RegionAnalyzer.Version selectedVersion = RegionAnalyzer.Version.values()[0];
        File inputFile = new File("region");
        for(String arg : args) {
            if(arg.equalsIgnoreCase("statistics")) {
                saveStatistics = true;
            } else if(arg.equalsIgnoreCase("no-hack")) {
                allowHack = false;
            } else if(arg.equalsIgnoreCase("table")) {
                generateTable = true;
            } else if(arg.equalsIgnoreCase("version-select")) {
                versionSelect = true;
            } else if(arg.toLowerCase().startsWith("version-select=")) {
                versionOverride = true;
                String v = arg.split("=", 2)[1].toUpperCase();
                try {
                    selectedVersion = RegionAnalyzer.Version.valueOf(v);
                } catch(IllegalArgumentException e) {
                    System.err.println("Invalid version: " + v);
                    System.err.print("Version must be one of the following: ");
                    for(RegionAnalyzer.Version version : RegionAnalyzer.Version.values()) {
                        System.err.print(version.name() + " ");
                    }
                    System.err.print("\n");
                    System.exit(1);
                }
            } else if(arg.toLowerCase().startsWith("blocks=")) {
                try {
                    for(String line : readLines(new FileInputStream(new File(arg.split("=", 2)[1])))) {
                        if(line.length() == 0) continue;
                        String[] split = line.split("=", 2);
                        BLOCK_NAMES.put(split[0], split[1]);
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            } else if(arg.toLowerCase().startsWith("merge=")) {
                try {
                    for(String line : readLines(new FileInputStream(new File(arg.split("=", 2)[1])))) {
                        if(line.length() == 0) continue;
                        BLOCKS_TO_MERGE.add(Integer.valueOf(line.trim()));
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            } else if(arg.equalsIgnoreCase("modernize-ids")) {
                modernizeIDs = true;
            } else if(arg.toLowerCase().startsWith("input=")) {
                inputOverride = true;
                inputFile = new File(arg.split("=", 2)[1]);
            } else {
                System.err.println("Unknown argument: " + arg);
            }
        }
        try {
            loadBlockNames();
            loadBlocksToMerge();
        } catch(IOException e) {
            e.printStackTrace();
        }
        System.out.println("Save statistics: " + saveStatistics + "\nAllow empty section hack: " + allowHack
                + "\nGenerate HTML table: " + generateTable + "\nVersion select: "
                + (versionOverride ? selectedVersion : versionSelect) + "\nModernize block IDs: " + modernizeIDs
                + "\nBlock IDs: " + BLOCK_NAMES.size() + "\nBlock IDs to merge: " + BLOCKS_TO_MERGE.size() + "\nInput: "
                + inputFile.getPath() + "\n--------------------------------");
        RegionAnalyzer analyzer;
        if(versionSelect) {
            Object returnedVersion = JOptionPane.showInputDialog(null,
                    "Select the format in which the region files were saved:", "Select Version",
                    JOptionPane.PLAIN_MESSAGE, null, RegionAnalyzer.Version.values(),
                    RegionAnalyzer.Version.ANVIL_2021);
            if(!(returnedVersion instanceof RegionAnalyzer.Version)) System.exit(0);
            selectedVersion = (RegionAnalyzer.Version) returnedVersion;
        }
        try {
            analyzer = selectedVersion.getAnalyzerInstance();
        } catch(InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }
        if(analyzer == null) analyzer = new RegionAnalyzerAnvil2021();
        if(inputOverride) {
            if(inputFile.isDirectory() != selectedVersion.usesDirectory()) {
                System.err.println("Input must be a " + (selectedVersion.usesDirectory() ? "directory" : "file") + ": "
                        + inputFile.getAbsolutePath());
                System.exit(1);
            }
        }
        analyzer.analyze(inputFile);
        System.out.println("Completed after " + millisToHMS(System.currentTimeMillis() - analyzer.getStartTime()));
    }

    public static String formatRegionName(File parent, File f) {
        return f.getPath().split(parent.getName())[1].substring(1);
    }

    /**
     * @author Estragon#9379
     * 
     * <p>Many thanks to Estragon#9379 on Discord for this code.</p>
     */
    public static int[] unstream(int bitsPerValue, int wordSize, boolean slack, long[] data) {
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

    public static int bitLength(int i) {
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

    public static String millisToHMS(long millis) {
        return String.format("%02d:%02d:%02d.%03d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1), millis % 1000);
    }

    private static void loadBlockNames() throws IOException {
        for(String line : readLines(Main.class.getResourceAsStream("/blocks.properties"))) {
            if(line.length() == 0) continue;
            String[] split = line.split("=", 2);
            BLOCK_NAMES.put(split[0], split[1]);
        }
    }

    private static void loadBlocksToMerge() throws IOException {
        for(String line : readLines(Main.class.getResourceAsStream("/merge.properties"))) {
            if(line.length() == 0) continue;
            BLOCKS_TO_MERGE.add(Integer.valueOf(line.trim()));
        }
    }

    public static String getStringID(String id) {
        return BLOCK_NAMES.getOrDefault(id, id);
    }

    public static List<String> readLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();
        return lines;
    }
}