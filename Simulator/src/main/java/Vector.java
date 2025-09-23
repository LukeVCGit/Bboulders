public record Vector(double x, double y) {

    public static final Vector ORIGIN = new Vector(0, 0);

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

    public double dist(Vector v) {
        double dx = x - v.x;
        double dy = y - v.y;
        return Math.sqrt(dx * dx + dy * dy);
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

    public double cross(Vector v) {
        return x * v.y - y * v.x;
    }

    //calculates the t and s values such that ||p0 + t*d0 - p1 - s*d1||^2 is minimised, d1 and d2 are assumed to be unit vectors
    public static Vector minTSLineDistance(Vector p1, Vector d1, Vector p2, Vector d2) {
        double a = d1.dot(d1);
        double b = d1.dot(d2);
        double c = d2.dot(d2);
        Vector w = p1.subtract(p2);
        double d = w.dot(d1);
        double e = w.dot(d2);

        double det = a*c-b*b;
        double t = 0;
        double s = 0;

        if (Math.abs(det) > 1e-6) { //if lines are not almost parallel
            t = (b*e-c*d)/det;
            s = (a*e-b*d)/det;
        }

        return new Vector(t,s);
    }

    //calculates the t and s values such that ||p0 + t*d0 - p1 - s*d1||^2 is minimised, where |t|<=tBound and |s|<=sBound, d1 and d2 are assumed to be unit vectors
    public static Vector boundedMinTSLineDistance(Vector p1, Vector d1, double tBound, Vector p2, Vector d2, double sBound) {

        //first case - unbounded solution is within bounds and valid
        Vector unboundedSoln = minTSLineDistance(p1, d1, p2, d2);
        if (Math.abs(unboundedSoln.x) <= tBound && Math.abs(unboundedSoln.y) <= sBound)
            return unboundedSoln;

        //we now consider all cases on the boundaries and in the corners
        double[] ts = new double[] { tBound, tBound, -tBound, -tBound, tBound, -tBound, 0, 0 };
        double[] ss = new double[] { sBound, -sBound, sBound, -sBound, 0, 0, sBound, -sBound };
        double[] dists = new double[8];

        //compute corner cases
        for (int i = 0; i < 4; i++) {
            Vector tPos = d1.scale(ts[i]).add(p1);
            Vector sPos = d2.scale(ss[i]).add(p2);
            dists[i] = tPos.dist(sPos);
        }

        //t on boundary, vary s
        for (int i = 4; i < 6; i++) {
            Vector fixedTPos = d1.scale(ts[i]).add(p1);
            double s = closestTtoPoint(p2, d2, fixedTPos);

            if (Math.abs(s) <= sBound) { //ensure s is in the bound
                Vector sPos = d2.scale(s).add(p2);
                dists[i] = fixedTPos.dist(sPos);
                ss[i] = s;
            } else {
                dists[i] = Double.MAX_VALUE;
            }
        }

        //s on boundary, vary t
        for (int i = 6; i < 8; i++) {
            Vector fixedSPos = d2.scale(ss[i]).add(p2);
            double t = closestTtoPoint(p1, d1, fixedSPos);

            if (Math.abs(t) <= tBound) { //ensure t is in the bound
                Vector tPos = d1.scale(t).add(p1);
                dists[i] = fixedSPos.dist(tPos);
                ts[i] = t;
            } else {
                dists[i] = Double.MAX_VALUE;
            }
        }

        //determine minimum from above cases
        double minT = ts[0];
        double minS = ss[0];
        double minDist = dists[0];
        for (int i = 1; i < 8; i++) {
            if (dists[i] < minDist) {
                minDist = dists[i];
                minT = ts[i];
                minS = ss[i];
            }
        }

        return new Vector(minT, minS);
    }
}
