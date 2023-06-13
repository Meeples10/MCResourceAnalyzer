package io.github.meeples10.mcresourceanalyzer;

import java.util.HashSet;
import java.util.Set;

public abstract class AnalyzerThread extends Thread {
    final Region r;
    final Set<Analysis> analyses = new HashSet<>();

    public AnalyzerThread(Region region) {
        r = region;
        setName(r.name);
    }

    public abstract void run();
}
