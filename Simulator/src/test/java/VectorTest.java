import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorTest {

    @Test
    void testPointExactlyOnLine() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(1, 0); // x-axis
        Vector point = new Vector(5, 0);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(5.0, t, 1e-9); // point (5,0) is at t=5
    }

    @Test
    void testPointOffLine() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(1, 0); // x-axis
        Vector point = new Vector(3, 4);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(3.0, t, 1e-9); // closest projection of (3,4) onto x-axis is (3,0)
    }

    @Test
    void testNegativeDirection() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(-1, 0); // negative x direction
        Vector point = new Vector(3, 0);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(-3.0, t, 1e-9); // because direction points left
    }

    @Test
    void testDegenerateDirection() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(0, 0); // degenerate direction
        Vector point = new Vector(3, 4);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(0.0, t, 1e-9); // should return 0 as per method
    }

    @Test
    void testPointBeforeStart() {
        Vector p = new Vector(2, 0);
        Vector d = new Vector(1, 0);
        Vector point = new Vector(0, 0);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(-2.0, t, 1e-9); // point projects backwards
    }
}