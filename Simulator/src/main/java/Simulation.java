import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class Simulation {
    public static void run(int fps, int secToRun) {
        //get python script path
        var res = Simulation.class.getResource("VisWithModernGL.py");
        String visPath = res.getPath().substring(1).replace("%20", " ");

        //create simulation data dir
        var simulationDataDir = Paths.get(visPath).getParent().resolve("simulation_data");
        simulationDataDir.toFile().mkdirs();

        double dt = 1.0/fps;

        for (File f : Objects.requireNonNull(simulationDataDir.toFile().listFiles()))
            f.delete();
        System.out.println("Cleared data directory");

        PDESolver.nx = 50; //todo make a way to change
        PDESolver.ny = 50;
        var eps = new ArrayList<EPS>();
        var cells = new ArrayList<Cell>();
        double angle = ThreadLocalRandom.current().nextDouble(0, 2*Math.PI);
        Cell initialCell = new Cell(new Vector (PDESolver.nx/2.0, PDESolver.ny/2.0), angle, EPS.l_ini);
        cells.add(initialCell);

        TimeStep step = new TimeStep(eps, cells);
        System.out.println("Set up initial condition");

        int totalSteps = secToRun*fps;
        for (int i = 0; i < totalSteps; i++) {
            step.startSaveAsync(simulationDataDir, i);
            step = step.next(dt);

            if (i % fps == 0)
                System.out.println("Completed timestep " + i + "/" + totalSteps);
        }
        System.out.println("Completed timestepping, saving data");

        TimeStep.saveFrames();
        System.out.println("Data saved");

        // Command and arguments as a list
        ProcessBuilder pb = new ProcessBuilder("python", visPath);
        pb.redirectErrorStream(true); // merge stdout + stderr

        Process process = null;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Pipe process output directly to console
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
