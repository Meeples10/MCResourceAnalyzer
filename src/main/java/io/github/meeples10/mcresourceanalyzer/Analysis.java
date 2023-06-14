package io.github.meeples10.mcresourceanalyzer;

import java.util.Hashtable;
import java.util.Map;

public class Analysis {
    public Map<String, Long> blocks = new Hashtable<>();
    public Map<String, Hashtable<Integer, Long>> heights = new Hashtable<>();
}
