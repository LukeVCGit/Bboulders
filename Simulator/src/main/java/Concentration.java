import java.util.List;

public class Concentration {
    private final PDESolver solver;

    public Concentration(double initialValue, int nx, int ny) {
        solver = new PDESolver(nx, ny);
        solver.setInitialCondition(initialValue);
    }

    public double getConcentration(double x, double y) {
        // Map floating-point coordinates to nearest grid index
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);

        // Clamp indices to valid range
        ix = Math.max(0, Math.min(solver.nx - 1, ix));
        iy = Math.max(0, Math.min(solver.ny - 1, iy));

        int index = ix * solver.ny + iy;
        return solver.getConcentration()[index];
    }

    public void advanceTimestep(double dt, List<Cell> cells) {
        solver.solveStep(dt, cells);
    }
}
