import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class VectorTest {
    private static final double EPS = 1e-9;
    private static final Random rng = new Random();

    @Test
    void testPointExactlyOnLine() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(1, 0); // x-axis
        Vector point = new Vector(5, 0);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(5.0, t, EPS); // point (5,0) is at t=5
    }

    @Test
    void testPointOffLine() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(1, 0); // x-axis
        Vector point = new Vector(3, 4);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(3.0, t, EPS); // closest projection of (3,4) onto x-axis is (3,0)
    }

    @Test
    void testNegativeDirection() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(-1, 0); // negative x direction
        Vector point = new Vector(3, 0);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(-3.0, t, EPS); // because direction points left
    }

    @Test
    void testDegenerateDirection() {
        Vector p = new Vector(0, 0);
        Vector d = new Vector(0, 0); // degenerate direction
        Vector point = new Vector(3, 4);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(0.0, t, EPS); // should return 0 as per method
    }

    @Test
    void testPointBeforeStart() {
        Vector p = new Vector(2, 0);
        Vector d = new Vector(1, 0);
        Vector point = new Vector(0, 0);

        double t = Vector.closestTtoPoint(p, d, point);
        assertEquals(-2.0, t, EPS); // point projects backwards
    }

    //helper method to generate random numbers in the range rand<|max|
    private static double rand(double bound) {
        return (rng.nextDouble()*2-1)*bound;
    }

    private static Vector randVec() {
        return new Vector(rand(5), rand(5));
    }

    //test that the lines do indeed intersect at s and t
    @Test
    public void testIntersect() {
        for (int i = 0; i < 100; i++) {
            Vector p1 = randVec();
            Vector p2 = randVec();
            Vector d1 = randVec();
            Vector d2 = randVec();

            Vector soln = Vector.minTSLineDistance(p1, d1, p2, d2);

            Vector tPos = d1.scale(soln.x()).add(p1);
            Vector sPos = d2.scale(soln.y()).add(p2);

            double dist = tPos.subtract(sPos).mag();

            assertEquals(0, dist, EPS);
        }
    }

    @Test
    public void intersectingLinesExpectedTS() {
        // line1: p1 + t*d1, p1=(0,0), d1=(1,0) (unit)
        // line2: p2 + s*d2, p2=(0,1), d2=(0,-1) (unit)
        // they intersect at (0,0) -> t = 0, s = 1
        Vector p1 = new Vector(0.0, 0.0);
        Vector d1 = new Vector(1.0, 0.0);
        Vector p2 = new Vector(0.0, 1.0);
        Vector d2 = new Vector(0.0, -1.0);

        Vector ts = Vector.minTSLineDistance(p1, d1, p2, d2);
        assertEquals(0.0, ts.x(), EPS, "t should be 0");
        assertEquals(1.0, ts.y(), EPS, "s should be 1");

        // residual should be near zero
        Vector closest1 = p1.add(d1.scale(ts.x()));
        Vector closest2 = p2.add(d2.scale(ts.y()));
        assertEquals(0.0, closest1.subtract(closest2).mag(), 1e-8, "closest points should coincide");
    }

    @Test
    public void parallelLines_returnsZeros() {
        // parallel lines (same direction vectors)
        Vector p1 = new Vector(0.0, 0.0);
        Vector d1 = new Vector(1.0, 0.0);
        Vector p2 = new Vector(0.0, 1.0);
        Vector d2 = new Vector(1.0, 0.0); // parallel to d1

        Vector ts = Vector.minTSLineDistance(p1, d1, p2, d2);

        // method returns (0,0) when det is near zero
        assertEquals(0.0, ts.x(), EPS, "t should be 0 for parallel lines (by method design)");
        assertEquals(0.0, ts.y(), EPS, "s should be 0 for parallel lines (by method design)");
    }

    @Test
    public void degenerateDirectionReturnsZeroes() {
        // degenerate case where one direction is zero vector
        Vector p1 = new Vector(0.0, 0.0);
        Vector d1 = new Vector(0.0, 0.0); // degenerate (not unit)
        Vector p2 = new Vector(1.0, 0.0);
        Vector d2 = new Vector(1.0, 0.0);

        // Should not throw, should return (0,0) according to method (det ~ 0)
        Vector ts = Vector.minTSLineDistance(p1, d1, p2, d2);

        assertEquals(0.0, ts.x(), EPS, "t should be 0 for degenerate direction (method returns default)");
        assertEquals(0.0, ts.y(), EPS, "s should be 0 for degenerate direction (method returns default)");
    }

    @Test
    public void residualOrthogonalToDirections() {
        // pick two non-parallel lines (intersecting in 2D)
        Vector p1 = new Vector(2.0, 1.0);
        Vector d1 = new Vector(1.0, 0.0);
        Vector p2 = new Vector(0.0, 0.0);
        Vector d2 = new Vector(0.0, 1.0);

        Vector ts = Vector.minTSLineDistance(p1, d1, p2, d2);

        // compute residual r = p1 + t d1 - p2 - s d2
        Vector r = p1.add(d1.scale(ts.x())).subtract(p2.add(d2.scale(ts.y())));

        // For the minimizing t,s (non-parallel case), r should be orthogonal to both d1 and d2:
        assertEquals(0.0, r.dot(d1), EPS, "residual should be orthogonal to d1");
        assertEquals(0.0, r.dot(d2), EPS, "residual should be orthogonal to d2");
    }

    private static final double TOLERANCE = 0.1;

    public void checkOptimal(Vector p1, Vector d1, double tBound, Vector p2, Vector d2, double sBound) {
        Vector soln = Vector.boundedMinTSLineDistance(p1, d1, tBound, p2, d2, sBound);
        Vector tPos = d1.scale(soln.x()).add(p1);
        Vector sPos = d2.scale(soln.y()).add(p2);

        double dist = tPos.subtract(sPos).mag();

        for (double t = -tBound; t <= tBound; t += 0.05) {
            for (double s = -sBound; s <= sBound; s += 0.05) {
                tPos = d1.scale(t).add(p1);
                sPos = d2.scale(s).add(p2);
                double checkedDist = tPos.subtract(sPos).mag();
                assertTrue(dist <= checkedDist + TOLERANCE, "Optimal distance not found. Guessed dist: " + dist + ", better solution: " + checkedDist);
            }
        }
    }

    @Test
    public void testOptimality() {
        for (int i = 0; i < 1000; i++) {
            Vector p1 = randVec();
            Vector p2 = randVec();
            Vector d1 = randVec();
            Vector d2 = randVec();

            checkOptimal(p1, d1, 1, p2, d2, 1);
        }
    }
}