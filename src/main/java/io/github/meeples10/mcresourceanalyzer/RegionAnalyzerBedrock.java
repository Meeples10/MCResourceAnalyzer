package io.github.meeples10.mcresourceanalyzer;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;

public class RegionAnalyzerBedrock extends RegionAnalyzer {
    private File dbFile;

    @Override
    public void validateInput(File input) {
        if(!input.exists()) {
            System.err.println("Error: No db directory found at " + input.getAbsolutePath());
            System.exit(1);
        }
        if(!input.isDirectory()) {
            System.err.println("Error: Input must be a directory");
            System.exit(1);
        }
        dbFile = input;
    }

    @Override
    public void findChunks(File input) {}

    @Override
    public void analyze() {
        Analysis a = new Analysis();
        DB db = null;
        try {
            db = factory.open(dbFile, new Options());

            DBIterator iterator = db.iterator();
            try {
                for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    byte[] key = iterator.peekNext().getKey();
                    if(key.length == 10) {
                        if(!(key[8] == 0x2F || (key.length >= 13 && key[12] == 0x2F))) continue;
                        byte subchunkY = key[key.length == 10 ? 9 : 13];
                        byte[] buffer = new byte[4];
                        byte[] subchunk = iterator.peekNext().getValue();
                        int paletteStart = findPaletteStart(subchunk);
                        byte[] blockData = new byte[paletteStart - 4];
                        System.arraycopy(subchunk, 4, blockData, 0, paletteStart - 4);
                        System.arraycopy(subchunk, paletteStart, buffer, 0, 4);

                        int paletteSize = btoi(buffer);
                        byte[] subarray = new byte[subchunk.length - paletteStart];
                        System.arraycopy(subchunk, paletteStart, subarray, 0, subarray.length);
                        String[] palette = extractBlockNames(subarray, paletteSize);

                        int blockSize = bitsPerBlock(paletteSize);
                        int[] blocks = unstream(blockSize, 8, false, blockData);
                        for(int y = 0; y < 16; y++) {
                            for(int z = 0; z < 16; z++) {
                                for(int x = 0; x < 16; x++) {
                                    int actualY = subchunkY * 16 + y;
                                    int block = blocks[y + z * 16 + x * 256];
                                    if(block >= paletteSize) block = 0; // FIXME ???

                                    String blockName = palette[block];
                                    if(a.blocks.containsKey(blockName)) {
                                        a.blocks.put(blockName, a.blocks.get(blockName) + 1L);
                                    } else {
                                        a.blocks.put(blockName, 1L);
                                    }
                                    if(!a.heights.containsKey(blockName)) {
                                        a.heights.put(blockName, new Hashtable<Integer, Long>());
                                    }
                                    if(a.heights.get(blockName).containsKey(actualY)) {
                                        a.heights.get(blockName).put(actualY,
                                                a.heights.get(blockName).get(actualY) + 1L);
                                    } else {
                                        a.heights.get(blockName).put(actualY, 1L);
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                iterator.close();
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            if(db != null) try {
                db.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        blockCounter.putAll(a.blocks);
        heightCounter.putAll(a.heights);
    }

    private static int btoi(byte[] b) {
        return (0xFF & b[0]) | (0xFF & b[1]) << 8 | (0xFF & b[2]) << 16 | (0xFF & b[3]) << 24;
    }

    private static int findPaletteStart(byte[] data) {
        for(int i = 0; i < data.length - 4; i++) {
            if(data[i] == 'n' && data[i + 1] == 'a' && data[i + 2] == 'm' && data[i + 3] == 'e') {
                return i - 10;
            }
        }
        return -1;
    }

    private static int bitsPerBlock(int paletteSize) {
        int[] p = new int[] { 1, 2, 3, 4, 5, 6, 8, 16 };
        int[] z = new int[] { 2, 4, 8, 16, 32, 64, 256, 65536 };

        int i;
        for(i = 0; i < 7 && z[i] < paletteSize; ++i) {}
        return p[i];
    }

    private static String[] extractBlockNames(byte[] data, int paletteSize) {
        String[] names = new String[paletteSize];
        int nameIndex = 0;
        int offset = 0;
        while(offset < data.length - 4 && nameIndex < paletteSize) {
            if(data[offset] == 'n' && data[offset + 1] == 'a' && data[offset + 2] == 'm' && data[offset + 3] == 'e') {
                byte[] name = new byte[(int) data[offset + 4] + 1];
                offset += 5;
                for(int i = 0; i < name.length; i++) {
                    name[i] = data[offset + i];
                }
                offset += name.length;
                names[nameIndex++] = new String(name);
            } else {
                offset++;
            }
        }
        return names;
    }

    private static int[] unstream(int bitsPerValue, int wordSize, boolean slack, byte[] data) {
        List<Integer> out = new ArrayList<Integer>();
        if(slack) {
            wordSize = (int) Math.floor(wordSize / bitsPerValue) * bitsPerValue;
        }
        int bl = 0;
        int v = 0;
        for(int i = 0; i < data.length; i++) {
            for(int n = 0; n < wordSize; n++) {
                int bit = (int) ((data[i] >> n) & 0x01);
                v = (bit << bl) | v;
                bl++;
                if(bl >= bitsPerValue) {
                    out.add(v);
                    v = 0;
                    bl = 0;
                }
            }
        }
        int[] array = new int[out.size()];
        for(int i = 0; i < out.size(); i++) {
            array[i] = out.get(i);
        }
        return array;
    }
}
