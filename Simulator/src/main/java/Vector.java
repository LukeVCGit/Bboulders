public class Vector {
    public final double x;
    public final double y;

    public static final Vector ORIGIN = new Vector(0, 0);

    public Vector(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vector scale(double scalar) {
        return new Vector(x * scalar, y * scalar);
    }

    public static Vector fromMag(double mag, double angle) {
        return new Vector(Math.cos(angle) * mag, Math.sin(angle) * mag);
    }

    public double mag() {
        return Math.sqrt(x * x + y * y);
    }

    public double angle() {
        return Math.atan2(y, x);
    }

    public Vector add(Vector v) {
        return new Vector(x + v.x, y + v.y);
    }

    public Vector subtract(Vector v) {
        return new Vector(x - v.x, y - v.y);
    }

    public Vector unitize() {
        double size = mag();
        return new Vector(x / size, y / size);
    }

    public static double closestTtoPoint(Vector p, Vector d, Vector point) {
        Vector w = point.subtract(p);
        double denom = d.dot(d);
        
        if (denom < 1e-12) //direction is degenerate
            return 0;
        
        return d.dot(w) / denom;
    }

    public double dot(Vector v) {
        return x * v.x + y * v.y;
    }

    @Override
    public String toString() {
        return "Vector{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Vector vector = (Vector) o;
        return Double.compare(x, vector.x) == 0 && Double.compare(y, vector.y) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        return result;
    }
}
