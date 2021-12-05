package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public abstract class RegionAnalyzer {
    public long chunkCount = 0;
    public Map<String, Long> blockCounter = new HashMap<String, Long>();
    public Map<String, HashMap<Integer, Long>> heightCounter = new HashMap<String, HashMap<Integer, Long>>();
    private long firstStartTime;

    public RegionAnalyzer() {
        firstStartTime = System.currentTimeMillis();
    }

    public abstract void analyze(File regionDir);

    public String generateTable(double totalBlocks, double totalExcludingAir) {
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
        return min;
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
        return max;
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
