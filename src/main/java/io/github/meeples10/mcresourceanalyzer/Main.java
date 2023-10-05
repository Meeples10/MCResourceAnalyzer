package io.github.meeples10.mcresourceanalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import picocli.CommandLine;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

public class Main {
    public static final FilenameFilter DS_STORE_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return !name.equals(".DS_Store");
        }
    };
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##########");
    public static final Map<String, String> BLOCK_NAMES = new HashMap<>();
    public static final List<Integer> BLOCKS_TO_MERGE = new ArrayList<>();
    static RegionAnalyzer.Version selectedVersion = RegionAnalyzer.Version.values()[0];
    static File inputFile = new File("region");
    static boolean saveStatistics = false;
    static boolean allowHack = true;
    static boolean generateTable = false;
    static boolean modernizeIDs = false;
    static boolean silent = false;
    static String outputPrefix = "";
    static String tableTemplatePath = "";
    static String tableTemplate = "";
    static int numThreads = 8;

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(createCommandSpec());
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setExecutionStrategy(Main::parseArgs);
        int exitCode = commandLine.execute(args);
        if(commandLine.getCommandName().equals("<main class>"))
            commandLine.setCommandName("java -jar mc-resource-analyzer-x.x.x.jar");
        if(commandLine.isUsageHelpRequested()) commandLine.usage(System.out);
        if(commandLine.isVersionHelpRequested()) commandLine.printVersionHelp(System.out);
        if(exitCode != 0 || commandLine.isUsageHelpRequested() || commandLine.isVersionHelpRequested())
            System.exit(exitCode);
        try {
            loadBlockNames();
            loadBlocksToMerge();
        } catch(IOException e) {
            e.printStackTrace();
        }
        Main.println("Save statistics: " + saveStatistics + "\nAllow empty section hack: " + allowHack
                + "\nGenerate HTML table: " + generateTable
                + (generateTable
                        ? ("\nTable template: " + (tableTemplatePath.equals("") ? "(none)" : tableTemplatePath))
                        : "")
                + "\nRegion version: " + selectedVersion + "\nModernize block IDs: " + modernizeIDs + "\nBlock IDs: "
                + BLOCK_NAMES.size() + "\nBlock IDs to merge: " + BLOCKS_TO_MERGE.size() + "\nInput: "
                + inputFile.getPath() + "\nOutput prefix: " + (outputPrefix.equals("") ? "(default)" : outputPrefix)
                + "\n--------------------------------");
        RegionAnalyzer analyzer;
        try {
            analyzer = selectedVersion.getAnalyzerInstance();
        } catch(InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }
        if(analyzer == null) analyzer = new RegionAnalyzerAnvil118();
        analyzer.setVersion(selectedVersion);
        if(inputFile.isDirectory() != selectedVersion.usesDirectory()) {
            System.err.println("Input must be a " + (selectedVersion.usesDirectory() ? "directory" : "file") + ": "
                    + inputFile.getAbsolutePath());
            System.exit(1);
        }
        analyzer.run(inputFile);
        Main.println("Completed after " + millisToHMS(System.currentTimeMillis() - analyzer.getStartTime()));
    }

    private static CommandSpec createCommandSpec() {
        CommandSpec spec = CommandSpec.create();
        spec.mixinStandardHelpOptions(true).versionProvider(new IVersionProvider() {
            @Override
            public String[] getVersion() throws Exception {
                List<String> lines = readLines(Main.class.getResourceAsStream("/version.properties"));
                return new String[] {
                        String.format("@|white,bold %s %s|@ @|faint %s|@", lines.get(0), lines.get(1), lines.get(2)),
                        String.format("@|yellow %s|@", lines.get(3)) };
            }
        });
        spec.addOption(
                OptionSpec.builder("-v", "--version-select").paramLabel("VERSION").type(RegionAnalyzer.Version.class)
                        .description("Selects the version with which the region files were generated.").build());
        spec.addOption(OptionSpec.builder("-o", "--output-prefix").paramLabel("STRING").type(String.class)
                .description("Adds a prefix to the program's output files.").build());
        spec.addOption(OptionSpec.builder("-t", "--table")
                .description("Generates a simple HTML table with the collected data.").build());
        spec.addOption(OptionSpec.builder("-T", "--table-template").paramLabel("PATH").type(String.class).description(
                "All instances of {{{TABLE}}} in the given file will be replaced by the table generated by the -t option.")
                .build());
        spec.addOption(OptionSpec.builder("-s", "--statistics")
                .description("Outputs a file with statistics about the analysis.").build());
        spec.addOption(
                OptionSpec.builder("-S", "--silent").description("Silences all output aside from errors.").build());
        spec.addOption(OptionSpec.builder("-m", "--modernize-ids")
                .description("If analyzing regions saved before 1.13, numeric block IDs will be replaced with"
                        + "their modern string representations (when possible).")
                .build());
        spec.addOption(OptionSpec.builder("-B", "--block-ids").paramLabel("PATH").type(String.class)
                .description("When using the -m option on a world with block IDs outside the range of 0-255,"
                        + "use this to specify the path to a file containing block IDs.")
                .build());
        spec.addOption(OptionSpec.builder("-M", "--merge-ids").paramLabel("PATH").type(String.class)
                .description("When analyzing a world with block IDs outside the range of 0-255, any block with an"
                        + "ID listed in this file will have all of its variants merged into a single value..")
                .build());
        spec.addOption(OptionSpec.builder("-H", "--no-hack").description(
                "The program attempts to compensate for inaccuracies at high Y values by assuming that empty chunk"
                        + "sections are filled with air. Use this option to disable this hack.")
                .build());
        spec.addOption(OptionSpec.builder("-n", "--num-threads").paramLabel("COUNT").type(int.class)
                .description("The maximum number of threads to use for analysis. (default: 8)").build());
        spec.addPositional(PositionalParamSpec.builder().paramLabel("INPUT").arity("0..1").type(String.class)
                .description("The region directory or .mclevel file to analyze. (default: 'region')").build());
        return spec;
    }

    private static int parseArgs(ParseResult pr) {
        if(pr.hasMatchedPositional(0)) inputFile = new File((String) pr.matchedPositional(0).getValue());
        saveStatistics = pr.hasMatchedOption('s');
        allowHack = !pr.hasMatchedOption('H');
        generateTable = pr.hasMatchedOption('t');
        modernizeIDs = pr.hasMatchedOption('m');
        silent = pr.hasMatchedOption('S');
        if(pr.hasMatchedOption('v')) selectedVersion = pr.matchedOption('v').getValue();
        if(pr.hasMatchedOption('o')) outputPrefix = pr.matchedOption('o').getValue();
        if(pr.hasMatchedOption('B')) {
            try {
                for(String line : readLines(new FileInputStream(new File((String) pr.matchedOption('B').getValue())))) {
                    if(line.length() == 0) continue;
                    String[] split = line.split("=", 2);
                    BLOCK_NAMES.put(split[0], split[1]);
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        if(pr.hasMatchedOption('M')) {
            try {
                for(String line : readLines(new FileInputStream(new File((String) pr.matchedOption('M').getValue())))) {
                    if(line.length() == 0) continue;
                    BLOCKS_TO_MERGE.add(Integer.valueOf(line.trim()));
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        if(pr.hasMatchedOption('T')) {
            File template = new File((String) pr.matchedOption('T').getValue());
            if(!template.exists() || template.isDirectory()) {
                System.err.println("Table template not found: " + template.getPath());
                System.exit(1);
            }
            if(template.isDirectory()) {
                System.err.println("Table template must be a file: " + template.getPath());
                System.exit(1);
            }
            tableTemplatePath = template.getPath();
            try {
                tableTemplate = String.join("\n", Files.readAllLines(template.toPath()));
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        if(pr.hasMatchedOption('n')) {
            numThreads = pr.matchedOption('n').getValue();
        }
        return 0;
    }

    public static String formatRegionName(File parent, File f) {
        return f.getPath().split(parent.getName())[1].substring(1);
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

    private static void loadBlockNames() throws IOException {
        for(String line : readLines(Main.class.getResourceAsStream("/blocks.properties"))) {
            if(line.length() == 0) continue;
            String[] split = line.split("=", 2);
            if(!BLOCK_NAMES.containsKey(split[0])) BLOCK_NAMES.put(split[0], split[1]);
        }
    }

    private static void loadBlocksToMerge() throws IOException {
        for(String line : readLines(Main.class.getResourceAsStream("/merge.properties"))) {
            if(line.length() == 0) continue;
            int i = Integer.valueOf(line.trim());
            if(!BLOCKS_TO_MERGE.contains(i)) BLOCKS_TO_MERGE.add(i);
        }
    }

    public static String getStringID(String id) {
        return BLOCK_NAMES.getOrDefault(id, id);
    }

    public static String getOutputPrefix() {
        return outputPrefix.equals("") ? "data" : outputPrefix;
    }

    public static List<String> readLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();
        return lines;
    }

    public static void print(Object s) {
        if(!silent) System.out.print(s);
    }

    public static void println(Object s) {
        if(!silent) System.out.println(s);
    }

    public static void printf(String format, Object... args) {
        if(!silent) System.out.printf(format, args);
    }
}
