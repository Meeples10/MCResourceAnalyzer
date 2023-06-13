package io.github.meeples10.mcresourceanalyzer;

public abstract class AnalyzerThread implements Runnable {
    Region region;
    Chunk chunk;

    public AnalyzerThread(Region region, Chunk chunk) {
        this.region = region;
        this.chunk = chunk;
    }

    public abstract void run();
}
