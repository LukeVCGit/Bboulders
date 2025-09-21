import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {//todo simulator class
        int fps = 30;
        int secToRunFor = 5;

        //G:\My Drive\Temp\Bogus Boulders Capstone\Visualisation\simulation_data
        if (args.length != 1) {
            var dir = Paths.get("C:\\Users\\bobje\\OneDrive - University of Cape Town\\CSC3003\\Capstone\\Bogus-Boulders\\BBoulders\\simulation_data")
                    ;
        }

        System.out.println("Running");
        var dir = Paths.get("C:\\Users\\bobje\\OneDrive - University of Cape Town\\CSC3003\\Capstone\\Bogus-Boulders\\BBoulders\\simulation_data");
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

        for (int i = 0; i < secToRunFor*fps; i++) {
            step.startSaveAsync(dir, i);
            step = step.next(dt);
        }
        System.out.println("Completed timestepping, saving data");

        TimeStep.saveFrames();
        System.out.println("Data saved");
    }
}
