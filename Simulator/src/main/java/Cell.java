import java.util.ArrayList;
import java.util.List;

//todo unit testing for everything
//todo improve names

public class Cell extends EPS {
    private final double angle;
    private final double length;

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
        double newAngle = angle; //todo add angular velocity

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

    public static double f(double C) {
        return C / (1+C);
    }

    @Override
    protected Vector resolveEPSCollision(EPS col) { //todo
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected Vector resolveCellCollision(Cell col) { //todo
        //throw new UnsupportedOperationException("Not supported yet.");

        double dx = position.x - col.position.x;
        double dy = position.y - col.position.y;
        double ux = Math.cos(angle);
        double uy = Math.sin(angle);
        double vx = Math.cos(col.angle);
        double vy = Math.sin(col.angle);

        double scale = -1/(ux*vy-uy*vx);
        double t = scale * (dx*vy - dy*vx); //current cell is t
        double s = scale * (dx*vy - dy*ux); //other cell is s

        // temp
        t = Math.clamp(t, -length*0.5, length*0.5);
        s = Math.clamp(s, -col.length*0.5, col.length*0.5);

        //closest point on each cell
        Vector p1 = Vector.fromMag(t, angle).add(position);
        Vector p2 = Vector.fromMag(s, col.angle).add(col.position);
        
        Vector disp = p1.subtract(p2);
        double dist = disp.mag();

        if (dist > d_0) //ignore collision if too far apart
            return Vector.ORIGIN;

        double h = d_0 - dist; //overlap between the two cells
        double forceMag = E * Math.sqrt(d_0 * h * h * h);

        return disp.unitize().scale(forceMag);
    }
}
