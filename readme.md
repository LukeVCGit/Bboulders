# How to Build and Run the Simulator
1. Install Java 24
2. Install maven and configure it to use Java 24
1. `cd Simulator`
2. Run `mvn exec:java -Dexec.mainClass="Main" -Dexec.args="\"<dest_dir>\""`, where `<dest_dir>` is the PRE-EXISTING directory where the Simulator output should be saved to