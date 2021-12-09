package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class RegionAnalyzer {
    private Version version;
    public long chunkCount = 0;
    public Map<String, Long> blockCounter = new HashMap<String, Long>();
    public Map<String, HashMap<Integer, Long>> heightCounter = new HashMap<String, HashMap<Integer, Long>>();
    private long firstStartTime;
    public long endTime;

    public RegionAnalyzer() {
        firstStartTime = System.currentTimeMillis();
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void run(File input) {
        analyze(input);

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

        double totalExcludingAir = (double) (totalBlocks - (blockCounter.containsKey("0") ? blockCounter.get("0") : 0)
                - (blockCounter.containsKey("minecraft:air") ? blockCounter.get("minecraft:air") : 0)
                - (blockCounter.containsKey("minecraft:cave_air") ? blockCounter.get("minecraft:cave_air") : 0));
        System.out.print("Generating CSV... ");
        String data = "id,";
        int minY = getMinimumY();
        int maxY = getMaximumY();
        for(int i = minY; i <= maxY; i++) {
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
            for(int i = minY; i <= maxY; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data += "0,";
                } else {
                    data += heightCounter.get(key).get(i) + ",";
                }
            }
            data += blockCounter.get(key) + ","
                    + Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / (double) totalBlocks) * 100.0d);
            if(key.equals("0") || key.equals("minecraft:air") || key.equals("minecraft:cave_air")
                    || key.equals("minecraft:void_air")) {
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
    }

    public abstract void analyze(File input);

    public String generateTable(double totalBlocks, double totalExcludingAir) {
        int minY = getMinimumY();
        int maxY = getMaximumY();
        String data = "<table>\n";
        data += "<tr><th>id</th><th>";
        for(int i = minY; i < maxY; i++) {
            data += i + "</th><th>";
        }
        data += "total</th><th>percent_of_total</th><th>percent_excluding_air</th></tr>\n<tr>";
        int digits = String.valueOf(blockCounter.size()).length();
        String completionFormat = "[%0" + digits + "d/%0" + digits + "d]";
        int keyIndex = 0;
        for(String key : heightCounter.keySet()) {
            keyIndex += 1;
            System.out.print("\rGenerating table... " + String.format(completionFormat, keyIndex, blockCounter.size()));
            data += "<td>" + (Main.modernizeIDs ? Main.getStringID(key) : key) + "</td>";
            for(int i = minY; i < maxY; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data += "<td>0</td>";
                } else {
                    data += "<td>" + heightCounter.get(key).get(i) + "</td>";
                }
            }
            data += "<td>" + blockCounter.get(key) + "</td><td>"
                    + Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / totalBlocks) * 100.0d) + "</td>";
            if(key.equals("minecraft:air") || key.equals("minecraft:cave_air")) {
                data += "<td>N/A</td>";
            } else {
                data += "<td>"
                        + Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / totalExcludingAir) * 100.0d)
                        + "</td>";
            }
            data += "</tr>\n<tr>";
        }
        return data.substring(0, data.length() - 4) + "</table>";
    }

    public long getStartTime() {
        return firstStartTime;
    }

    static boolean mergeStates(int id) {
        return Main.BLOCKS_TO_MERGE.contains((int) id);
    }

    static boolean mergeStates(byte id) {
        return mergeStates((int) id);
    }

    /* THIS IS A HACK TO ACCOUNT FOR NONEXISTENT SECTIONS AT HIGH Y VALUES */
    void airHack(int sectionY, String airID) {
        if(Main.allowHack && sectionY < 15) {
            if(!blockCounter.containsKey(airID)) blockCounter.put(airID, 0L);
            if(!heightCounter.containsKey(airID)) heightCounter.put(airID, new HashMap<Integer, Long>());
            for(; sectionY < 16; sectionY++) {
                blockCounter.put(airID, blockCounter.get(airID) + 4096L);
                for(int y = sectionY * 16; y < sectionY * 16 + 16; y++) {
                    if(heightCounter.get(airID).containsKey(y)) {
                        heightCounter.get(airID).put(y, heightCounter.get(airID).get(y) + 256L);
                    } else {
                        heightCounter.get(airID).put(y, 256L);
                    }
                }
            }
        }
    }

    int getMinimumY() {
        int min = Integer.MAX_VALUE;
        for(HashMap<Integer, Long> map : heightCounter.values()) {
            for(int i : map.keySet()) {
                if(i < min) {
                    min = i;
                }
            }
        }
        if(version == Version.ANVIL_2021 || version == Version.ANVIL_118) {
            return min < 0 ? min : 0;
        } else {
            return 0;
        }
    }

    int getMaximumY() {
        int max = Integer.MIN_VALUE;
        for(HashMap<Integer, Long> map : heightCounter.values()) {
            for(int i : map.keySet()) {
                if(i > max) {
                    max = i;
                }
            }
        }
        if(version == Version.INDEV || version == Version.ALPHA || version == Version.MCREGION) {
            return 128;
        } else {
            return max > 255 ? max : 255;
        }
    }

    public enum Version {
        ANVIL_118("Anvil (1.18)", RegionAnalyzerAnvil118.class, true), ANVIL_2021("Anvil (1.16 to 1.17)",
                RegionAnalyzerAnvil2021.class, true), ANVIL_2018("Anvil (1.13 to 1.15)", RegionAnalyzerAnvil2018.class,
                        true), ANVIL_2012("Anvil (1.2 to 1.12)", RegionAnalyzerAnvil2012.class, true), MCREGION(
                                "McRegion (Beta 1.3 to 1.1)", RegionAnalyzerMCRegion.class,
                                true), ALPHA("Alpha (Infdev 20100327 to Beta 1.2)", RegionAnalyzerAlpha.class,
                                        true), INDEV("Indev (Indev 0.31 20100122 to Infdev 20100325)",
                                                RegionAnalyzerIndev.class, false);

        private final String versionName;
        private final Class<? extends RegionAnalyzer> analyzerClass;
        private final boolean usesDirectory;

        private Version(String versionName, Class<? extends RegionAnalyzer> analyzerClass, boolean usesDirectory) {
            this.versionName = versionName;
            this.analyzerClass = analyzerClass;
            this.usesDirectory = usesDirectory;
        }

        public String toString() {
            return versionName;
        }

        public RegionAnalyzer getAnalyzerInstance() throws InstantiationException, IllegalAccessException {
            return analyzerClass.newInstance();
        }

        public boolean usesDirectory() {
            return usesDirectory;
        }
    }
}
