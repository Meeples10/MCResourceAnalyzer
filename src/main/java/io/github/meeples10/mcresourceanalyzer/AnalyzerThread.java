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
        setName(r.name);
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
                long count = a.blocks.get(blockName);
                if(out.blocks.containsKey(blockName)) {
                    out.blocks.put(blockName, out.blocks.get(blockName) + count);
                } else {
                    out.blocks.put(blockName, count);
                }
            }
            for(String blockName : a.heights.keySet()) {
                if(!out.heights.containsKey(blockName)) {
                    out.heights.put(blockName, new Hashtable<Integer, Long>());
                }
                for(int y : a.heights.get(blockName).keySet()) {
                    long count = a.heights.get(blockName).get(y);
                    if(out.heights.get(blockName).containsKey(y)) {
                        out.heights.get(blockName).put(y, out.heights.get(blockName).get(y) + count);
                    } else {
                        out.heights.get(blockName).put(y, count);
                    }
                }
            }
        }
        return out;
    }
}
