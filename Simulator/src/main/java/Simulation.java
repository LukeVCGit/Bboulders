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
    private static final int FPS = 30;

    public static void run(int secToRun, int simSize, double motility) {
        //get python script path
        var res = Simulation.class.getResource("VisWithModernGL.py");
        String visPath = res.getPath().substring(1).replace("%20", " ");

        //create simulation data dir
        var simulationDataDir = Paths.get(visPath).getParent().resolve("simulation_data");
        simulationDataDir.toFile().mkdirs();

        //delete old simulation files
        for (File f : Objects.requireNonNull(simulationDataDir.toFile().listFiles()))
            f.delete();
        System.out.println("Cleared data directory");

        PDESolver.nx = simSize;
        PDESolver.ny = simSize;
        EPS.motility = motility;

        System.out.println("Starting simulation");
        long simStartTime = System.nanoTime();
        var eps = new ArrayList<EPS>();
        var cells = new ArrayList<Cell>();
        double angle = ThreadLocalRandom.current().nextDouble(0, 2*Math.PI);
        Cell initialCell = new Cell(new Vector (PDESolver.nx/2.0, PDESolver.ny/2.0), angle, EPS.l_ini);
        cells.add(initialCell);

        TimeStep step = new TimeStep(eps, cells);
        System.out.println("Set up initial condition");

        double dt = 1.0/FPS;
        int totalSteps = secToRun*FPS;
        for (int i = 0; i < totalSteps; i++) {
            step.startSaveAsync(simulationDataDir, i);
            step = step.next(dt);

            if (i % 100 == 0)
                System.out.println("Completed timestep " + i + "/" + totalSteps);
        }

        double simTime = (System.nanoTime() - simStartTime)/1_000_000_000.0;
        double realTimeFactor = secToRun/simTime;
        System.out.printf("Completed timestepping in %.3f sec at %.3fx real time speed%n", simTime, realTimeFactor);
        System.out.println("Saving timestep data...");

        TimeStep.saveFrames();
        System.out.println("Data saved");

        // Command and arguments as a list
        ProcessBuilder pb = new ProcessBuilder("python", "-u", visPath);
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
