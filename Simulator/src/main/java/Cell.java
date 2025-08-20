public class Cell extends EPS {
    private double angle;
    private double length;

    public Cell(Vector position, double angle, double length) {
        super(position);
        this.angle = angle;
        this.length = length;
    }
}
