package io.github.meeples10.mcresourceanalyzer;

import java.util.HashMap;
import java.util.Map;

public class Analysis {
    public Map<String, Long> blocks = new HashMap<String, Long>();
    public Map<String, HashMap<Integer, Long>> heights = new HashMap<String, HashMap<Integer, Long>>();
}
