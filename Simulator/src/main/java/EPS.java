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
    protected static final double E = 3e3; //1e3 works nicely

    // derived constants
    protected static final Random rng = new Random();
    public static final double d_eps = 0.5 * d_0;
    public static final double L_MAX = l_c + d_0;
    protected static final double r_0 = d_0 * 0.5;
    protected static final double l_ini = 0.5 * l_c;
    protected static final double Abar = r_0 * (Math.PI * r_0 + 1.5 * l_c);

    protected final Vector position;

    public Vector getPosition() {
        return position;
    }

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

    private Vector F_rf(List<EPS> potentialColliders) {
        Vector sum = Vector.ORIGIN;

        for (EPS col : potentialColliders) {
            if (position.equals(col.position)) //valid collisions are not with oneself
                continue;
            if (position.dist(col.position) > EPS.L_MAX) //ignore collisions too far away
                continue;

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

    protected Vector resolveCellCollision(Cell col) {
        Vector cellDir = Vector.fromMag(1, col.angle);

        //find the closest point on the cell's main axis to the eps
        double t = Vector.closestTtoPoint(col.position, cellDir, position);
        double tb = col.length*0.5; //bound of t
        t = Math.clamp(t, -tb, tb);

        Vector closestPoint = Vector.fromMag(t, col.angle).add(col.position);

        Vector disp = position.subtract(closestPoint);
        double dist = disp.mag();

        double overlapThreshold = 0.5*(d_0 + d_eps);

        if (dist > overlapThreshold) //if the cell and eps particle are not close enough
            return Vector.ORIGIN;

        double h = overlapThreshold - dist;
        double forceMag = E * Math.sqrt(d_eps * h * h * h);

        return disp.unitize().scale(forceMag);
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

        List<EPS> output = new ArrayList<>(1);
        output.add(copy);
        return output;
    }
}
