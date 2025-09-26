import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Cell extends EPS {
    protected static final double DAUGHTER_ANGLE_RANGE = 20 * (Math.PI / 180);

    final double angle;
    final double length;
    private transient double torque = 0;

    public double getAngle() {
        return angle;
    }

    public double getLength() {
        return length;
    }

    public Cell(Vector position, double angle, double length) {
        super(position);
        this.angle = angle;
        this.length = length;
    }

    @Override
    public List<EPS> tick(double dt, List<EPS> potentialColliders, double C) {
        //update the quantities using forward euler
        double newLength = length + dlength_dt(C)*dt;
        Vector newPos = vel(potentialColliders).scale(dt).add(position);
        double newAngle = angle + angularVelocity()*dt;

        List<EPS> cells = new ArrayList<EPS>(2);

        if (newLength >= l_c) { //split condition
            Vector daughterPos = Vector.fromMag(newLength*0.25 + 0.5*r_0, newAngle).add(newPos);
            double daughterAngle = newAngle + (ThreadLocalRandom.current().nextDouble() -0.5) * DAUGHTER_ANGLE_RANGE;
            cells.add(new Cell(daughterPos,  daughterAngle, (newLength-d_0)*0.5));

            newPos = Vector.fromMag(-newLength*0.25 - 0.5*r_0, newAngle).add(newPos);
            newAngle += (ThreadLocalRandom.current().nextDouble()-0.5) * DAUGHTER_ANGLE_RANGE;
            newLength = (newLength-d_0)*0.5;
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

    @Override
    public double area() {
        return r_0 * (Math.PI * r_0 + 2 * length);
    }

    private double dlength_dt(double C) {
        return phi * area() * f(C) / Abar;
    }

    private double angularVelocity() {
        return torque/(nu * Math.pow(L(), 3));
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
    protected Vector resolveCellCollision(Cell col) {
        Vector d1 = Vector.fromMag(1, angle);
        Vector d2 = Vector.fromMag(1, col.angle);

        Vector pair = Vector.boundedMinTSLineDistance(position, d1, length*0.5, col.position, d2, col.length*0.5);

        //closest point on each cell
        Vector tPos = Vector.fromMag(pair.x(), angle).add(position);
        Vector sPos = Vector.fromMag(pair.y(), col.angle).add(col.position);
        
        Vector disp = tPos.subtract(sPos);
        double dist = disp.mag();

        if (dist > d_0) //ignore collision if too far apart
            return Vector.ORIGIN;
        else if (dist < 1e-6) //special case for disp if dist == 0 to prevent NaN error
            disp = position.subtract(col.position);

        double h = d_0 - dist; //overlap between the two cells
        double forceMag = E * Math.sqrt(d_0 * h * h * h);
        Vector force = disp.unitize().scale(forceMag);

        Vector forceAlongAxis = d1.scale(force.dot(d1));
        torque += tPos.cross(force);

        return forceAlongAxis;
    }
}
