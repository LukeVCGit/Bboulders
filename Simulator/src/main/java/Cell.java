import java.util.List;

public class Cell extends EPS {
    private double angle;
    private double length;

    public Cell(Vector position, double angle, double length) {
        super(position);
        this.angle = angle;
        this.length = length;
    }

    @Override
    public List<EPS> tick(double dt, List<EPS> potentialColliders) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
