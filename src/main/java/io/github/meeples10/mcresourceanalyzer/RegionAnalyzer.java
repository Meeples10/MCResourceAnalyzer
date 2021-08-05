package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class RegionAnalyzer {
    private static final List<Integer> BLOCKS_TO_MERGE = Arrays.asList(8, 9, 10, 11, 23, 26, 29, 33, 36, 39, 40, 46, 50,
            51, 53, 54, 55, 59, 60, 61, 62, 63, 64, 65, 66, 68, 69, 70, 71, 72, 75, 76, 77, 78, 81, 83, 84, 86, 90, 91,
            93, 94, 96, 104, 105, 106, 107, 115, 117, 118, 120, 127, 131, 132, 134, 135, 136, 139, 140, 141, 142, 143,
            144, 146, 147, 148, 149, 150, 151, 154, 158, 163, 164, 167, 176, 177, 183, 184, 185, 186, 187, 178, 193,
            194, 195, 196, 197, 198, 200, 207, 212, 218, 255);
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

    boolean mergeStates(byte id) {
        return BLOCKS_TO_MERGE.contains((int) id);
    }

    public enum Version {
        ANVIL_2021("Anvil (1.16 to 1.17)", RegionAnalyzerAnvil2021.class), MCREGION("McRegion (Beta 1.3 to 1.1)",
                RegionAnalyzerMCRegion.class), ALPHA("Alpha (Infdev 20100327 to Beta 1.2)",
                        RegionAnalyzerAlpha.class), INDEV("Indev (Indev 0.31 20100122 to Infdev 20100325)",
                                RegionAnalyzerIndev.class);

        private final String versionName;
        private final Class<? extends RegionAnalyzer> analyzerClass;

        private Version(String versionName, Class<? extends RegionAnalyzer> analyzerClass) {
            this.versionName = versionName;
            this.analyzerClass = analyzerClass;
        }

        public String toString() {
            return versionName;
        }

        public RegionAnalyzer getAnalyzerInstance() throws InstantiationException, IllegalAccessException {
            return analyzerClass.newInstance();
        }
    }
}
