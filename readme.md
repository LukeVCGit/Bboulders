# How to Build and Run the Simulator
1. Install Java 24
2. Install maven and configure it to use Java 24
1. `cd Simulator`
2. Run `mvn exec:java -Dexec.mainClass="Main" -Dexec.args="\"<dest_dir>\""`, where `<dest_dir>` is the PRE-EXISTING directory where the Simulator output should be saved to

# How to build and run the Visualiser
1. Ensure Python 3.9 is installed
2. Install imageio and imageio[ffmpeg]
3. Run `python visualisation.py simulation_data frames --output biofilm.mp4` where `simulation_data` is the directory of all json files, `frames` is the directory of all pngs produced and `biofilm.mp4` is the output video 
