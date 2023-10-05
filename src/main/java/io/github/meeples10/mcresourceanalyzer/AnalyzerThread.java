package io.github.meeples10.mcresourceanalyzer;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

public abstract class AnalyzerThread extends Thread {
    final RegionAnalyzer ra;
    final Region r;
    final Set<Analysis> analyses = new HashSet<>();

    public AnalyzerThread(RegionAnalyzer ra, Region region) {
        this.ra = ra;
        r = region;
    }

    public void run() {
        process();
        ra.add(combineAnalyses());
    }

    public abstract void process();

    private Analysis combineAnalyses() {
        Analysis out = new Analysis();
        for(Analysis a : analyses) {
            for(String blockName : a.blocks.keySet()) {
                out.blocks.put(blockName, out.blocks.getOrDefault(blockName, 0L) + a.blocks.get(blockName));
            }
            for(String blockName : a.heights.keySet()) {
                if(!out.heights.containsKey(blockName)) {
                    out.heights.put(blockName, new Hashtable<Integer, Long>());
                }
                for(int y : a.heights.get(blockName).keySet()) {
                    out.heights.get(blockName).put(y,
                            out.heights.get(blockName).getOrDefault(y, 0L) + a.heights.get(blockName).get(y));
                }
            }
        }
        return out;
    }
}
