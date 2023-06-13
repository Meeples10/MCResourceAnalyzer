package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class RegionAnalyzer {
    private Version version;
    public int chunkCount = 0;
    public Map<String, Long> blockCounter = new HashMap<String, Long>();
    public Map<String, HashMap<Integer, Long>> heightCounter = new HashMap<String, HashMap<Integer, Long>>();
    private long firstStartTime;
    public long duration;
    List<Region> regions = new ArrayList<>();
    Set<AnalyzerThread> threads = new HashSet<>();
    private AtomicInteger completed = new AtomicInteger(0);

    public RegionAnalyzer() {
        firstStartTime = System.currentTimeMillis();
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void run(File input) {
        validateInput(input);

        Main.print("Scanning for chunks... ");
        findChunks(input);

        for(Region r : regions) {
            chunkCount += r.size();
        }
        Main.printf("%d region%s, %d chunk%s found\n", regions.size(), regions.size() == 1 ? "" : "s", chunkCount,
                chunkCount == 1 ? "" : "s");

        analyze();

        if(threads.size() > 0) {
            System.out.println();
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(Main.numThreads, 1024));
            for(AnalyzerThread t : threads) {
                pool.submit(t);
            }
            pool.shutdown();
            try {
                pool.awaitTermination(100, TimeUnit.DAYS);
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
            for(AnalyzerThread t : threads) {
                for(Analysis a : t.analyses) {
                    for(String s : a.blocks.keySet()) {
                        blockCounter.put(s, blockCounter.getOrDefault(s, 0L) + a.blocks.get(s));
                    }
                    for(String s : a.heights.keySet()) {
                        if(heightCounter.containsKey(s)) {
                            Map<Integer, Long> existing = heightCounter.get(s);
                            Map<Integer, Long> heights = a.heights.get(s);
                            for(int i : heights.keySet()) {
                                existing.put(i, existing.getOrDefault(i, 0L) + heights.get(i));
                            }
                        } else {
                            heightCounter.put(s, a.heights.get(s));
                        }
                    }
                }
            }
        }

        duration = System.currentTimeMillis() - getStartTime();
        Main.println("Completed analysis in " + Main.millisToHMS(duration));

        long totalBlocks = 0L;
        for(String key : blockCounter.keySet()) {
            totalBlocks += blockCounter.get(key);
        }
        Main.println("--------------------------------\n" + blockCounter.size() + " unique blocks\n" + totalBlocks
                + " blocks total\n--------------------------------");

        Main.print("Sorting data... ");
        heightCounter = heightCounter.entrySet().stream().sorted(Map.Entry.comparingByKey(new Comparator<String>() {
            @Override
            public int compare(String arg0, String arg1) {
                return Long.compare(blockCounter.get(arg1), blockCounter.get(arg0));
            }
        })).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        Main.println("Done");

        double totalExcludingAir = (double) (totalBlocks - (blockCounter.containsKey("0") ? blockCounter.get("0") : 0)
                - (blockCounter.containsKey("minecraft:air") ? blockCounter.get("minecraft:air") : 0)
                - (blockCounter.containsKey("minecraft:cave_air") ? blockCounter.get("minecraft:cave_air") : 0));
        Main.print("Generating CSV... ");
        StringBuilder data = new StringBuilder();
        data.append("id,");
        int minY = getMinimumY();
        int maxY = getMaximumY();
        for(int i = minY; i <= maxY; i++) {
            data.append(i);
            data.append(",");
        }
        data.append("total,percent_of_total,percent_excluding_air\n");
        for(String key : heightCounter.keySet()) {
            data.append(Main.modernizeIDs ? Main.getStringID(key) : key);
            data.append(",");
            for(int i = minY; i <= maxY; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data.append("0,");
                } else {
                    data.append(heightCounter.get(key).get(i));
                    data.append(",");
                }
            }
            data.append(blockCounter.get(key));
            data.append(",");
            data.append(Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / (double) totalBlocks) * 100.0d));
            if(key.equals("0") || key.equals("minecraft:air") || key.equals("minecraft:cave_air")
                    || key.equals("minecraft:void_air")) {
                data.append(",N/A");
            } else {
                data.append(",");
                data.append(Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / totalExcludingAir) * 100.0d));
            }
            data.append("\n");
        }
        Main.println("Done");
        try {
            File out = new File(Main.getOutputPrefix() + ".csv");
            Main.writeStringToFile(out, data.toString());
            Main.println("CSV written to " + out.getAbsolutePath());
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        if(Main.generateTable) {
            try {
                File out = new File(Main.getOutputPrefix() + ".html");
                Main.writeStringToFile(out, generateTable((double) totalBlocks, totalExcludingAir));
                Main.println("\nTable written to " + out.getAbsolutePath());
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        if(Main.saveStatistics) {
            try {
                Main.writeStringToFile(new File(Main.getOutputPrefix() + "_stats.txt"),
                        "chunk-count=" + chunkCount + "\nunique-blocks=" + blockCounter.size() + "\ntotal-blocks="
                                + totalBlocks + "\nduration-millis=" + (duration) + "\nduration-readable="
                                + Main.millisToHMS(duration));
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public abstract void validateInput(File input);

    public abstract void findChunks(File input);

    public abstract void analyze();

    public synchronized void updateProgress() {
        int c = completed.incrementAndGet() + 1;
        if(c > chunkCount) return;
        Main.print("Analyzing chunks [" + c + "/" + chunkCount + "]\r");
        if(c == chunkCount) System.out.println("\n");
    }

    public synchronized void halt() {
        for(AnalyzerThread t : threads) {
            t.interrupt();
        }
        System.exit(1);
    }

    public String generateTable(double totalBlocks, double totalExcludingAir) {
        int minY = getMinimumY();
        int maxY = getMaximumY();
        StringBuilder data = new StringBuilder();
        if(Main.tableTemplate.isEmpty()) {
            data.append("<table>\n");
        }
        data.append("<tr><th>id</th><th>");
        for(int i = minY; i < maxY; i++) {
            data.append(i);
            data.append("</th><th>");
        }
        data.append("total</th><th>percent_of_total</th><th>percent_excluding_air</th></tr>\n<tr>");
        int digits = String.valueOf(blockCounter.size()).length();
        String completionFormat = "[%0" + digits + "d/%0" + digits + "d]";
        int keyIndex = 0;
        for(String key : heightCounter.keySet()) {
            keyIndex += 1;
            Main.print("\rGenerating table... " + String.format(completionFormat, keyIndex, blockCounter.size()));
            data.append("<td>");
            data.append(Main.modernizeIDs ? Main.getStringID(key) : key);
            data.append("</td>");
            for(int i = minY; i < maxY; i++) {
                if(!heightCounter.get(key).containsKey(i)) {
                    data.append("<td>0</td>");
                } else {
                    data.append("<td>");
                    data.append(heightCounter.get(key).get(i));
                    data.append("</td>");
                }
            }
            data.append("<td>");
            data.append(blockCounter.get(key));
            data.append("</td><td>");
            data.append(Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / totalBlocks) * 100.0d));
            data.append("</td>");
            if(key.equals("minecraft:air") || key.equals("minecraft:cave_air")) {
                data.append("<td>N/A</td>");
            } else {
                data.append("<td>");
                data.append(Main.DECIMAL_FORMAT.format(((double) blockCounter.get(key) / totalExcludingAir) * 100.0d));
                data.append("</td>");
            }
            data.append("</tr>\n<tr>");
        }
        String out = data.substring(0, data.length() - 4);
        if(Main.tableTemplate.isEmpty()) {
            return out + "</table>";
        } else {
            return Main.tableTemplate.replace("{{{TABLE}}}", out);
        }
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
    static void airHack(Analysis a, int sectionY, String airID) {
        if(Main.allowHack && sectionY < 15) {
            if(!a.blocks.containsKey(airID)) a.blocks.put(airID, 0L);
            if(!a.heights.containsKey(airID)) a.heights.put(airID, new HashMap<Integer, Long>());
            for(; sectionY < 16; sectionY++) {
                a.blocks.put(airID, a.blocks.get(airID) + 4096L);
                for(int y = sectionY * 16; y < sectionY * 16 + 16; y++) {
                    if(a.heights.get(airID).containsKey(y)) {
                        a.heights.get(airID).put(y, a.heights.get(airID).get(y) + 256L);
                    } else {
                        a.heights.get(airID).put(y, 256L);
                    }
                }
            }
        }
    }

    int getMinimumY() {
        int min = Integer.MAX_VALUE;
        for(Map<Integer, Long> map : heightCounter.values()) {
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
        for(Map<Integer, Long> map : heightCounter.values()) {
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
        ANVIL_118(RegionAnalyzerAnvil118.class, true),
        ANVIL_2021(RegionAnalyzerAnvil2021.class, true),
        ANVIL_2018(RegionAnalyzerAnvil2018.class, true),
        ANVIL_2012(RegionAnalyzerAnvil2012.class, true),
        MCREGION(RegionAnalyzerMCRegion.class, true),
        ALPHA(RegionAnalyzerAlpha.class, true),
        INDEV(RegionAnalyzerIndev.class, false);

        private final Class<? extends RegionAnalyzer> analyzerClass;
        private final boolean usesDirectory;

        private Version(Class<? extends RegionAnalyzer> analyzerClass, boolean usesDirectory) {
            this.analyzerClass = analyzerClass;
            this.usesDirectory = usesDirectory;
        }

        public RegionAnalyzer getAnalyzerInstance() throws InstantiationException, IllegalAccessException {
            return analyzerClass.newInstance();
        }

        public boolean usesDirectory() {
            return usesDirectory;
        }
    }
}
