import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

public class Simulation {
    public static void run(int fps, int secToRun, Path dir) {
        double dt = 1.0/fps;

        for (File f : Objects.requireNonNull(dir.toFile().listFiles()))
            f.delete();
        System.out.println("Cleared data directory");

        var eps = new ArrayList<EPS>();
        for (int i = 0; i < 10; i++)
            eps.add(new EPS(Vector.ORIGIN));

        var cells = new ArrayList<Cell>();
        cells.add(new Cell(Vector.ORIGIN, 0, EPS.l_ini));

        TimeStep step = new TimeStep(eps, cells);
        System.out.println("Set up initial condition");

        for (int i = 0; i < secToRun*fps; i++) {
            step.startSaveAsync(dir, i);
            step = step.next(dt);
        }
        System.out.println("Completed timestepping, saving data");

        TimeStep.saveFrames();
        System.out.println("Data saved");
    }
}
