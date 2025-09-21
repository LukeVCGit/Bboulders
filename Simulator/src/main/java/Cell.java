import java.util.ArrayList;
import java.util.List;

//todo unit testing for everything
//todo improve names

public class Cell extends EPS {
    final double angle;
    final double length;

    private transient double torque = 0;

    public Cell(Vector position, double angle, double length) {
        super(position);
        this.angle = angle;
        this.length = length;
    }

    @Override
    public List<EPS> tick(double dt, List<EPS> potentialColliders) {
        //update the quantities using forward euler
        double newLength = length + dlength_dt()*dt;
        Vector newPos = vel(potentialColliders).scale(dt).add(position);
        double newAngle = angle + angularVelocity()*dt;

        List<EPS> cells = new ArrayList<EPS>();

        if (newLength >= l_c) { //split condition
            Vector daughterPos = Vector.fromMag(newLength*0.25, newAngle).add(newPos);
            double daughterAngle = newAngle + (rng.nextDouble()-0.5) * DAUGHTER_ANGLE_RANGE;
            cells.add(new Cell(daughterPos,  daughterAngle, l_ini));

            newPos = Vector.fromMag(-newLength*0.25, newAngle).add(newPos);
            newAngle += (rng.nextDouble()-0.5) * DAUGHTER_ANGLE_RANGE;
            newLength = l_ini;
        }

        cells.add(new Cell(newPos, newAngle, newLength));

        return cells;
    }

    @Override
    protected double L() {
        return d_0 + length;
    }

    @Override
    protected Vector F_mf() {
        return Vector.fromMag(motility, angle);
    }

    public double area() {
        return r_0 * (Math.PI * r_0 + 2 * length);
    }

    private double dlength_dt() {
        return phi * area() * f(temp_C) / Abar;
    }

    private double angularVelocity() {
        return torque*12/(nu * Math.pow(L(), 3));
    }

    public static double f(double C) {
        return C / (1+C);
    }

    @Override
    protected Vector resolveEPSCollision(EPS col) {
        Vector cellDir = Vector.fromMag(1, angle);

        //find the closest point on the cell's main axis to the eps
        double t = Vector.closestTtoPoint(position, cellDir, col.position);
        double tb = length*0.5; //bound t
        t = Math.clamp(t, -tb, tb);

        Vector closestPoint = Vector.fromMag(t, angle).add(position);

        Vector disp = closestPoint.subtract(col.position);
        double dist = disp.mag();

        double overlapThreshold = 0.5*(d_0 + d_eps);

        if (dist > overlapThreshold) //if not close enough
            return Vector.ORIGIN;

        double h = overlapThreshold - dist;
        double forceMag = E*Math.sqrt(d_0 * h * h * h);
        Vector force = disp.unitize().scale(forceMag);

        //compute the component of the force in the direction of the cell's axis
        Vector forceAlongAxis = cellDir.scale(force.dot(cellDir));

        //todo check correct
        //calculate torque on cell
        torque += closestPoint.cross(force);

        return forceAlongAxis;
    }

    @Override
    protected Vector resolveCellCollision(Cell col) { //todo
        Vector d1 = Vector.fromMag(1, angle);
        Vector d2 = Vector.fromMag(1, col.angle);

        double a = d1.dot(d1);
        double b = d1.dot(d2);
        double c = d2.dot(d2);
        Vector w = position.subtract(col.position);
        double d = w.dot(d1);
        double e = w.dot(d2);

        double det = a*c-b*b;
        double t = 0;
        double s = 0;

        if (Math.abs(det) > 1e-6) { //if lines are not almost parallel
            t = (b*e-c*d)/det;
            s = (a*e-b*d)/det;
        }

        //bounds of t and s
        double tb = length*0.5;
        double sb = col.length*0.5;

        // temp
        t = Math.clamp(t, -tb, tb);
        s = Math.clamp(s, -sb, sb);

        //closest point on each cell
        Vector tPos = Vector.fromMag(t, angle).add(position);
        Vector sPos = Vector.fromMag(s, col.angle).add(col.position);
        
        Vector disp = tPos.subtract(sPos);
        double dist = disp.mag();

        if (dist > d_0) //ignore collision if too far apart
            return Vector.ORIGIN;

        double h = d_0 - dist; //overlap between the two cells
        double forceMag = E * Math.sqrt(d_0 * h * h * h);

        if (dist == 0.0) //special case for disp if dist == 0 to prevent NaN error
            disp = w;

        return disp.unitize().scale(forceMag);
    }
}
