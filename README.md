# Minecraft Resource Analyzer

For more information, see [this page](https://meeples10.github.io/resource-distribution.html).

This is a standalone program created to replace [this plugin](https://github.com/Meeples10/ChunkAnalyzer).

## Using the program

1. Clone the repository and build it with Maven or [download the latest release here](https://github.com/Meeples10/MCResourceAnalyzer/releases).
2. If your world was generated with a version after Beta 1.3, copy the `region` directory from your Minecraft world into the same directory as the program (`mc-resource-analyzer-x.x.x.jar`).
If the world was generated with a version between Infdev 20100327 and Beta 1.2, rename the entire world directory to `region` and copy it to the same directory as the program.
If the world was generated with Indev, rename the save file to `world.mclevel` and copy it to the same directory as the program.
3. Run the JAR with `java -jar mc-resource-analyzer-x.x.x.jar`, or by double-clicking it. After analyzing the regions, the program will create a file in the same directory named `data.csv`.

Note that the numbers for `minecraft:air` may be inaccurate at high Y values due to how Minecraft stores chunks.

### Command line arguments

Several command line arguments may be used to modify the behavior of the program. Multiple arguments can be used at once (e.g. `java -jar mc-resource-analyzer-x.x.x.jar table air-hack`). All available arguments are listed below.

- `statistics`: Adds a line with statistics at the beginning of the `data.csv` file.
- `no-hack`: The program attempts to compensate for the aforementioned inaccuracies at high Y values by assuming that empty chunk sections are filled with air. Use this argument to disable this hack.
- `table`: Generates a simple HTML table with the collected data.
- `version-select`: Use this argument if you want to analyze a world that was not generated with the latest version of Minecraft. Shows a popup on launch that allows the version in which the region files were generated to be selected. Selecting a version that does not match the version in which the regions were generated may result in unexpected behavior.
- `modernize-ids`: If analyzing regions saved before 1.13, numeric block IDs will be replaced with their modern string representations. If no string corresponding to the numeric ID is found, the numeric ID will be saved instead.

## Version compatibility

MCResourceAnalyzer 1.1.0 can analyze worlds generated with any version of Minecraft: Java Edition between Indev 0.31 20100122 and 1.17.
