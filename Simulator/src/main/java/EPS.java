import java.util.ArrayList;
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
    protected static final double E = 1e4; //1e3 works nicely

    // derived constants
    protected static final Random rng = new Random();
    public static final double d_eps = 0.5 * d_0;
    public static final double L_MAX = l_c + d_0;
    protected static final double r_0 = d_0 * 0.5;
    protected static final double l_ini = 0.5 * l_c;
    protected static final double Abar = r_0 * (Math.PI * r_0 + 1.5 * l_c);

    protected final Vector position;

    public EPS(Vector position) {
        this.position = position; //todo clamp position to be within boundary
    }

    private Vector epsilon() {
        double mag = rng.nextDouble() * 2e-3 - 1e-3;
        double ang = 2*Math.PI*rng.nextDouble();
        return Vector.fromMag(mag, ang);
    }

    protected double L() {
        return d_eps;
    }

    private Vector F_rf(List<EPS> potentialColliders) {
        Vector sum = Vector.ORIGIN;

        for (EPS col : potentialColliders) {
            if (col.getClass() != Cell.class)
                sum = sum.add(resolveEPSCollision(col));
            else
                sum = sum.add(resolveCellCollision((Cell)col));
        }

        return sum;
    }

    protected Vector resolveEPSCollision(EPS col) {
        Vector disp = position.subtract(col.position);
        double dist = disp.mag();

        if (dist > L()) //if the two eps particles are not close enough
            return Vector.ORIGIN;

        double h = d_eps - dist; //overlap between the two eps
        double forceMag = E * Math.sqrt(d_eps * h * h * h);

        return disp.unitize().scale(forceMag);
    }

    protected Vector resolveCellCollision(Cell col) { //todo
        throw new UnsupportedOperationException("Not supported yet.");
    }

    protected Vector F_mf() { //no motility force
        return Vector.ORIGIN;
    }

    protected final Vector vel(List<EPS> potentialColliders) {
        return F_mf()
                .add(epsilon())
                .add(F_rf(potentialColliders))
                .scale(1/(nu*L()));
    }

    public List<EPS> tick(double dt, List<EPS> potentialColliders) {
        //update EPS position using forward euler
        Vector newPos = vel(potentialColliders).scale(dt).add(position);
        EPS copy = new EPS(newPos);

        List<EPS> output = new ArrayList<>();
        output.add(copy);
        return output;
    }
}
