package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

class Region {
    public final RegionFile file;
    public final Set<Chunk> chunks = new HashSet<>();

    public Region(File file) {
        this.file = new RegionFile(file);
    }

    public void addChunk(int x, int z) {
        chunks.add(new Chunk(x, z));
    }

    public int size() {
        return chunks.size();
    }
}
