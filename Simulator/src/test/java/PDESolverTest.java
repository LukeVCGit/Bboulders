import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PDESolverTest {

    @Test
    void testInitialConditionAtFloatingPointPositions() {
        List<Cell> cells = new ArrayList<>();
        Concentration concPDE = new Concentration(2.5, cells);

        assertEquals(2.5, concPDE.getConcentration(0.0, 0.0), 1e-12);
        assertEquals(2.5, concPDE.getConcentration(25.3, 25.7), 1e-12); // maps to nearest grid
        assertEquals(2.5, concPDE.getConcentration(49.9, 49.1), 1e-12); // clamped to 49
    }

    @Test
    void testAdvanceTimestepDoesNotProduceNaN() {
        List<Cell> cells = new ArrayList<>();
        Concentration concPDE = new Concentration(3.0, cells);

        concPDE.advanceTimestep(0.1, cells);

        assertFalse(Double.isNaN(concPDE.getConcentration(0.0, 0.0)));
        assertFalse(Double.isNaN(concPDE.getConcentration(25.5, 25.5)));
        assertFalse(Double.isInfinite(concPDE.getConcentration(49.9, 49.9)));
    }

    @Test
    void testConcentrationChangesAfterStep() {
        List<Cell> cells = new ArrayList<>();
        Concentration concPDE = new Concentration(3.0, cells);

        double before = concPDE.getConcentration(25.2, 25.8);
        concPDE.advanceTimestep(0.5, cells);
        double after = concPDE.getConcentration(25.2, 25.8);

        assertNotEquals(before, after, "Concentration should change after timestep");
    }
}
