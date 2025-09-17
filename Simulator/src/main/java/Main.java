import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {
        //G:\My Drive\Temp\Bogus Boulders Capstone\Visualisation\simulation_data
        if (args.length != 1) {
            System.out.println("One argument is required: existing destination directory for timesteps of the simulation");
            return;
        }

        System.out.println("Running");
        var dir = Paths.get(args[0]);
        double dt = 1.0/30;

        for (File f : Objects.requireNonNull(dir.toFile().listFiles()))
            f.delete();
        System.out.println("Cleared data directory");

        var eps = new ArrayList<EPS>();
//        for (int i = 0; i < 10; i++)
//            eps.add(new EPS(Vector.ORIGIN));

        var cells = new ArrayList<Cell>();
        cells.add(new Cell(Vector.ORIGIN, 0, EPS.l_ini));

        TimeStep step = new TimeStep(eps, cells);
        System.out.println("Set up initial condition");

        for (int i = 0; i < 60; i++) {
            step.save(dir, i);
            step = step.next(dt);
        }
        System.out.println("Completed timestepping");
    }
}
