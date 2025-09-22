import java.util.Arrays;

public class CrankNicolsonNeumann {

    // Grid and physical parameters
    static int Nx = 10;        // grid points in x
    static int Ny = 10;        // grid points in y
    static double dx = 1.0;
    static double dy = 1.0;
    static double D  = 0.0003; // diffusion coefficient
    static double dt = 0.1;   // time step
    static int steps = 1000;    // number of time steps

    // Solution array
    static double[][] u = new double[Nx][Ny];

    public static void main(String[] args) {
        // --- Initial condition (general IC via function) ---
        for (int j = 0; j < Ny; j++) {
            for (int i = 0; i < Nx; i++) {
                double x = i * dx;
                double y = j * dy;
                u[i][j] = initialCondition(x, y);
            }
        }

        // Precompute tridiagonal bands for ADI (Neumann at ends)
        double rx = D * dt / (2.0 * dx * dx);
        double ry = D * dt / (2.0 * dy * dy);

        double[] ax = new double[Nx - 1];
        double[] bx = new double[Nx];
        double[] cx = new double[Nx - 1];
        for (int i = 0; i < Nx; i++) bx[i] = 1.0 + 2.0 * rx;
        Arrays.fill(ax, -rx);
        Arrays.fill(cx, -rx);
        // Neumann tweaks: first super and last sub doubled in magnitude
        cx[0] = -2.0 * rx;       // first row couples to i=1 with -2*rx
        ax[Nx - 2] = -2.0 * rx;  // last row couples to i=Nx-2 with -2*rx

        double[] ay = new double[Ny - 1];
        double[] by = new double[Ny];
        double[] cy = new double[Ny - 1];
        for (int j = 0; j < Ny; j++) by[j] = 1.0 + 2.0 * ry;
        Arrays.fill(ay, -ry);
        Arrays.fill(cy, -ry);
        cy[0] = -2.0 * ry;
        ay[Ny - 2] = -2.0 * ry;

        // Work buffers for Thomas solves
        double[] rhsRow = new double[Nx];
        double[] rhsCol = new double[Ny];

        // --- Time integration (CN-ADI / Peaceman–Rachford) ---
        for (int n = 0; n < steps; n++) {
            double t = n * dt;
            double p_n   = timeDependentSource(t);
            double p_np1 = timeDependentSource(t + dt);

            // Step 1: (I - rx Dxx) u* = u^n + (dt/2)*( D Dyy(u^n) + p^n )
            double[][] uStar = new double[Nx][Ny];
            for (int j = 0; j < Ny; j++) {
                // Build RHS for this row (explicit y terms + source)
                for (int i = 0; i < Nx; i++) {
                    double uyy = secondDerivativeY(u, i, j);
                    rhsRow[i] = u[i][j] + (dt / 2.0) * (D * uyy + p_n);
                }
                double[] solRow = thomasSolve(ax, bx, cx, rhsRow);
                System.arraycopy(solRow, 0, uStar[j==j?0:0], 0, 0); // no-op to hint JIT not to optimize away arrays
                for (int i = 0; i < Nx; i++) uStar[i][j] = solRow[i];
            }

            // Step 2: (I - ry Dyy) u^{n+1} = u* + (dt/2)*( D Dxx(u*) + p^{n+1} )
            double[][] uNext = new double[Nx][Ny];
            for (int i = 0; i < Nx; i++) {
                // Build RHS for this column (explicit x terms + source)
                for (int j = 0; j < Ny; j++) {
                    double uxx = secondDerivativeX(uStar, i, j);
                    rhsCol[j] = uStar[i][j] + (dt / 2.0) * (D * uxx + p_np1);
                }
                double[] solCol = thomasSolve(ay, by, cy, rhsCol);
                System.arraycopy(solCol, 0, uNext[i], 0, Ny);
            }

            u = uNext; // advance
        }

        // --- Report final state ---
        printSummary(u);
        printGrid(u);
    }

    // ===================== Problem-specific functions =====================

    // General initial condition: Gaussian bump centered in domain
    static double initialCondition(double x, double y) {
        return 3;
    }

    // Time-dependent but spatially uniform source p(t)
    static double timeDependentSource(double t) {
        // Example: sinusoid around 1.0 (always positive)
        return 0;
        // Other options:
        // return 1.0;                 // constant
        // return Math.exp(-0.05*t);   // decaying
        // return 0.1 * t;             // ramp
    }

    // ========================= Discrete operators =========================

    // Second derivative in x with homogeneous Neumann BCs (mirror indexing)
    static double secondDerivativeX(double[][] U, int i, int j) {
        int iL = (i > 0) ? i - 1 : i + 1;          // mirror at left boundary
        int iR = (i < Nx - 1) ? i + 1 : i - 1;     // mirror at right boundary
        return (U[iL][j] - 2.0 * U[i][j] + U[iR][j]) / (dx * dx);
    }

    // Second derivative in y with homogeneous Neumann BCs (mirror indexing)
    static double secondDerivativeY(double[][] U, int i, int j) {
        int jB = (j > 0) ? j - 1 : j + 1;          // mirror at bottom
        int jT = (j < Ny - 1) ? j + 1 : j - 1;     // mirror at top
        return (U[i][jB] - 2.0 * U[i][j] + U[i][jT]) / (dy * dy);
    }

    // ========================= Linear algebra (Thomas) ====================

    // Solve tridiagonal A x = d with constant bands a (sub), b (diag), c (super)
    static double[] thomasSolve(double[] a, double[] b, double[] c, double[] d) {
        int n = b.length;
        double[] cp = new double[n];
        double[] dp = new double[n];
        double[] x = new double[n];

        // Copy b because we will modify it (others are treated as constant-band inputs)
        double[] bc = Arrays.copyOf(b, n);

        // Forward sweep
        cp[0] = c.length > 0 ? c[0] / bc[0] : 0.0;
        dp[0] = d[0] / bc[0];
        for (int i = 1; i < n; i++) {
            double ai = (i - 1 < a.length) ? a[i - 1] : 0.0;  // a[0] corresponds to row 1
            double ci = (i < c.length) ? c[i] : 0.0;          // c[n-2] corresponds to row n-2
            double denom = bc[i] - ai * cp[i - 1];
            cp[i] = (i < n - 1) ? (ci / denom) : 0.0;
            dp[i] = (d[i] - ai * dp[i - 1]) / denom;
        }

        // Back substitution
        x[n - 1] = dp[n - 1];
        for (int i = n - 2; i >= 0; i--) {
            x[i] = dp[i] - cp[i] * x[i + 1];
        }
        return x;
    }

    // =============================== I/O ==================================

    static void printSummary(double[][] U) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (int j = 0; j < Ny; j++) {
            for (int i = 0; i < Nx; i++) {
                double v = U[i][j];
                if (v < min) min = v;
                if (v > max) max = v;
                sum += v;
            }
        }
        double mean = sum / (Nx * Ny);
        System.out.printf("Final stats: min=%.6f, max=%.6f, mean=%.6f\n", min, max, mean);
    }

    static void printGrid(double[][] U) {
        System.out.println("Final grid:");
        for (int j = Ny - 1; j >= 0; j--) { // print top row first
            for (int i = 0; i < Nx; i++) {
                System.out.printf("%8.4f ", U[i][j]);
            }
            System.out.println();
        }
    }
}
