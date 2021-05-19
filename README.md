# MinecraftResourceAnalyzer

For more information, see [this page](https://meeples10.github.io/resource-distribution.html).

This is a standalone program created to replace [this plugin](https://github.com/Meeples10/ChunkAnalyzer).

## Using the program

1. Clone the repository and build it with Maven or [download the latest release here](https://github.com/Meeples10/MCResourceAnalyzer/releases).
2. Copy the `region` directory from your Minecraft world into the same directory as the program (`mc-resource-analyzer-x.x.x.jar`).
3. Run the JAR with `java -jar mc-resource-analyzer-x.x.x.jar`, or by double-clicking it. After analyzing the regions, the program will create a file in the same directory named `data.csv`.

To include a line with statistics at the beginning of the `data.csv` file, run the JAR with `java -jar mc-resource-analyzer-x.x.x.jar statistics`.

Note that the numbers for `minecraft:air` may be inaccurate at high Y values due to how Minecraft stores chunks. The program attempts to compensate for this by assuming that empty chunk sections are filled with air. To disable these assumptions and only save the actual data read from the region files, run the JAR with `java -jar mc-resource-analyzer-x.x.x.jar no-hack`. Multiple command line options can be used at once (e.g. `java -jar mc-resource-analyzer-x.x.x.jar statistics no-hack`).
