import java.util.ArrayList;
import java.util.List;

public class ConcentrationPDE {
    private final PDE solver;
    public ConcentrationPDE(double initialValue, List<Cell> list) {
        solver = new PDE(list);
        solver.setInitialCondition(initialValue);
    }

    public double getConcentration(double x, double y) {
        int nx = 50; // match PDE grid size
        int ny = 50;

        // Map floating-point coordinates to nearest grid index
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);

        // Clamp indices to valid range
        ix = Math.max(0, Math.min(nx - 1, ix));
        iy = Math.max(0, Math.min(ny - 1, iy));

        int index = ix * ny + iy;
        return solver.getConcentrationCopy().get(index);
    }

    public void advanceTimestep(double dt, List<Cell> cell) {
        solver.solveStep(dt);
    }
}
