package io.github.meeples10.mcresourceanalyzer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

public class Main {
    public static DateFormat date = new SimpleDateFormat("dd MMM yyyy 'at' hh:mm:ss a zzz");
    public static DecimalFormat decimalFormat = new DecimalFormat("0.##########");
    static boolean saveStatistics = false;
    static boolean allowHack = true;
    static boolean generateTable = false;
    static boolean versionSelect = false;

    public static void main(String[] args) {
        decimalFormat.setMaximumFractionDigits(10);
        for(String arg : args) {
            if(arg.equalsIgnoreCase("statistics")) {
                saveStatistics = true;
            } else if(arg.equalsIgnoreCase("no-hack")) {
                allowHack = false;
            } else if(arg.equalsIgnoreCase("table")) {
                generateTable = true;
            } else if(arg.equalsIgnoreCase("version-select")) {
                versionSelect = true;
            }
        }
        System.out.println("Save statistics: " + saveStatistics + "\nAllow empty section hack: " + allowHack
                + "\nGenerate HTML table: " + generateTable + "\nVersion select: " + versionSelect
                + "\n--------------------------------");
        RegionAnalyzer analyzer;
        if(versionSelect) {
            Object selectedVersion = JOptionPane.showInputDialog(null,
                    "Select the format in which the region files were saved", "Select Version",
                    JOptionPane.PLAIN_MESSAGE, null, RegionAnalyzer.Version.values(),
                    RegionAnalyzer.Version.ANVIL_2021);
            if(!(selectedVersion instanceof RegionAnalyzer.Version)) System.exit(0);
            switch((RegionAnalyzer.Version) selectedVersion) {
            case ANVIL_2021:
                analyzer = new RegionAnalyzerAnvil2021();
                break;
            case MCREGION:
                analyzer = new RegionAnalyzerMCRegion();
                break;
            default:
                System.err.println("Unknown version: " + selectedVersion);
                System.exit(1);
                return;
            }
        } else {
            analyzer = new RegionAnalyzerAnvil2021();
        }
        analyzer.analyze(new File("region"));
        System.out.println("Completed after " + millisToHMS(System.currentTimeMillis() - analyzer.getStartTime()));
    }

    public static String formatRegionName(File f) {
        return f.getPath().split("region")[1].substring(1);
    }

    /**
     * @author Estragon#9379
     * 
     * <p>Many thanks to Estragon#9379 on Discord for this code.</p>
     */
    public static int[] unstream(int bitsPerValue, int wordSize, boolean slack, long[] data) {
        // in: bits per value, word size, ignore spare bits, data
        // out: decoded array
        List<Integer> out = new ArrayList<Integer>();
        if(slack) {
            wordSize = (int) Math.floor(wordSize / bitsPerValue) * bitsPerValue;
        }
        int bl = 0;
        int v = 0;
        for(int i = 0; i < data.length; i++) {
            for(int n = 0; n < wordSize; n++) {
                int bit = (int) ((data[i] >> n) & 0x01);
                // v = (v << 1) | bit;
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

    public static int bitLength(int i) {
        return (int) (Math.log(i) / Math.log(2) + 1);
    }

    /**
     * @param file the output file
     * @param data the string to write to the file
     */
    public static void writeStringToFile(File file, String data) throws IOException {
        FileWriter out = null;
        try {
            out = new FileWriter(file);
            out.write(data);
        } catch(IOException e) {
            throw new IOException(e);
        } finally {
            if(out != null) try {
                out.close();
            } catch(IOException ignore) {}
        }
    }

    public static String millisToHMS(long millis) {
        return String.format("%02d:%02d:%02d.%03d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1), millis % 1000);
    }
}