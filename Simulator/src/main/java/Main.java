import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {//todo simulator class
        int fps = 30;
        int secToRunFor = 10;

        //G:\My Drive\Temp\Bogus Boulders Capstone\Visualisation\simulation_data
        if (args.length != 1) {
            System.out.println("One argument is required: existing destination directory for timesteps of the simulation");
            return;
        }

        System.out.println("Running");
        var dir = Paths.get(args[0]);
        Simulation.run(fps, secToRunFor, dir);
    }
}
