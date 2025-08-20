import java.util.List;
import java.util.Random;

public class EPS {
    //constants
    protected static final double d_0 = 1;
    protected static final double l_c = 5;
    protected static final double motility = 300;
    protected static final double phi = 3.5;
    protected static final double nu = 200;
    protected static final double temp_C = 3; // todo implement PDE
    protected static final double DAUGHTER_ANGLE_RANGE = 45 * (Math.PI / 180);
    protected static final double E = 2e3;

    // derived constants
    protected static final Random rng = new Random();
    protected static final double d_eps = 0.5 * d_0;
    protected static final double r_eps = 0.5 * d_eps;

    protected Vector position;

    public EPS(Vector position) {
        this.position = position;
    }

    private Vector epsilon() {
        double mag = rng.nextDouble() * 2e-3 - 1e-3;
        double ang = 2*Math.PI*rng.nextDouble();
        return Vector.fromMag(mag, ang);
    }

    protected double L() {
        return d_eps;
    }

    protected Vector F_rf(List<EPS> potentialColliders) { //todo implement
        Vector sum = Vector.ORIGIN;

        for (EPS col : potentialColliders) {
            if (col.getClass() != Cell.class)
                sum = sum.add(resolveEPSCollision(col));
            else
                sum = sum.add(resolveEPSCellCollision((Cell)col));
        }

        return sum;
    }

    private Vector resolveEPSCollision(EPS col) {
        return Vector.ORIGIN;
    }

    private Vector resolveEPSCellCollision(Cell col) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    protected Vector F_mf() { //no motility force
        return Vector.ORIGIN;
    }

    protected Vector vel(List<EPS> potentialColliders) {
        return F_mf()
                .add(epsilon())
                .add(F_rf(potentialColliders))
                .scale(1/(nu*L()));
    }
}
