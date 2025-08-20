import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {
        System.out.println("Running");
        var dir = Paths.get("G:\\My Drive\\Temp\\Bogus Boulders Capstone\\Visualisation\\simulation_data");
        double dt = 1.0/30;

        for (File f : Objects.requireNonNull(dir.toFile().listFiles()))
            f.delete();
        System.out.println("Cleared data directory");

        var eps = new ArrayList<EPS>();
        for (int i = 0; i < 10; i++)
            eps.add(new EPS(Vector.ORIGIN));

        TimeStep step = new TimeStep(eps,new ArrayList<>());
        System.out.println("Set up initial condition");

        for (int i = 0; i < 60; i++) {
            step.save(dir, i);
            step = step.next(dt);
        }
        System.out.println("Completed timestepping");
    }
}
