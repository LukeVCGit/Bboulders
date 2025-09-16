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
        return Vector.ORIGIN;
    }
}
