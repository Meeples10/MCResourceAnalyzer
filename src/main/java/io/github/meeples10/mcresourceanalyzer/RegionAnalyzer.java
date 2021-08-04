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
                    + Main.decimalFormat.format(((double) blockCounter.get(key) / totalBlocks) * 100.0d) + "</td>";
            if(key.equals("minecraft:air") || key.equals("minecraft:cave_air")) {
                data += "<td>N/A</td>";
            } else {
                data += "<td>"
                        + Main.decimalFormat.format(((double) blockCounter.get(key) / totalExcludingAir) * 100.0d)
                        + "</td>";
            }
            data += "</tr>\n<tr>";
        }
        return data.substring(0, data.length() - 4) + "</table>";
    }

    public long getStartTime() {
        return firstStartTime;
    }

    public enum Version {
        ANVIL_2021("Anvil (1.16 to 1.17)"), MCREGION("McRegion (Beta 1.3 to 1.1)");

        private final String versionName;

        private Version(String versionName) {
            this.versionName = versionName;
        }

        public String toString() {
            return versionName;
        }
    }
}
