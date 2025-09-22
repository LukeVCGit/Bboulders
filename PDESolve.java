import java.util.Arrays;

public class PDESolve {

    static final int Nx = 5;    // grid points in x
    static final int Ny = 5;    // grid points in y
    static double dx = 1.0;
    static double dy = 1.0;
    static double D = 0.0003;    // diffusion coefficient
    static double dt = 0.1;
    static int steps = 100;

    static int N = Nx * Ny; // total unknowns

    public static void main(String[] args) {
        // Initial condition: user-defined function
        
        double[] u = new double[N];
        for (int j = 0; j < Ny; j++) {
            for (int i = 0; i < Nx; i++) {
                double x = i * dx;
                double y = j * dy;
                u[index(i, j)] = initialCondition(x, y);
            }
        }

        // Assemble Laplacian with Neumann BCs
        double[][] L = buildLaplacian();

        // Build matrices A and B
        double alpha = D * dt / 2.0;
        double[][] A = new double[N][N];
        double[][] B = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                A[i][j] = (i == j ? 1.0 : 0.0) - alpha * L[i][j];
                B[i][j] = (i == j ? 1.0 : 0.0) + alpha * L[i][j];
            }
        }

        // Time stepping
        for (int n = 0; n < steps; n++) {
            double t = n * dt; //REMOVE THIS VALUE FOR WHEN ROB WANTS TO CALL HIS VALUES FOR P
            double pNow = timeDependentSource(t);   // uniform source value at time t

            double[] rhs = matVec(B, u);
            for (int i = 0; i < N; i++) rhs[i] += dt * pNow;
            u = solveCG(A, rhs, 1e-8, 500);
        }

        // Print final solution (flattened grid)
        for (int j = 0; j < Ny; j++) {
            for (int i = 0; i < Nx; i++) {
                System.out.printf("%.3f ", u[index(i, j)]);
            }
            System.out.println();
        }
        
    }

    // Example initial condition function
    static double initialCondition(double x, double y) {
        // Gaussian bump at center
        double cx = (Nx - 1) * dx / 2.0;
        double cy = (Ny - 1) * dy / 2.0;
        return Math.exp(-((x - cx) * (x - cx) + (y - cy) * (y - cy)));

        // Other options:
        // return (x < (Nx*dx)/2 ? 1.0 : 0.0);   // step function
        // return Math.random();                 // random IC
    }

    // Example time-dependent source term p(t)
    static double timeDependentSource(double newP) {
        return newP;
    }

    // Construct Laplacian with Neumann BCs (5-point stencil)
    static double[][] buildLaplacian() {
        double[][] L = new double[N][N];
        double invdx2 = 1.0 / (dx * dx);
        double invdy2 = 1.0 / (dy * dy);

        for (int j = 0; j < Ny; j++) {
            for (int i = 0; i < Nx; i++) {
                int k = index(i, j);
                double diag = 0.0;

                // Left neighbor
                if (i > 0) {
                    L[k][index(i - 1, j)] += invdx2;
                    diag -= invdx2;
                } else {
                    L[k][index(i + 1, j)] += invdx2;
                    diag -= invdx2;
                }

                // Right neighbor
                if (i < Nx - 1) {
                    L[k][index(i + 1, j)] += invdx2;
                    diag -= invdx2;
                } else {
                    L[k][index(i - 1, j)] += invdx2;
                    diag -= invdx2;
                }

                // Bottom neighbor
                if (j > 0) {
                    L[k][index(i, j - 1)] += invdy2;
                    diag -= invdy2;
                } else {
                    L[k][index(i, j + 1)] += invdy2;
                    diag -= invdy2;
                }

                // Top neighbor
                if (j < Ny - 1) {
                    L[k][index(i, j + 1)] += invdy2;
                    diag -= invdy2;
                } else {
                    L[k][index(i, j - 1)] += invdy2;
                    diag -= invdy2;
                }

                L[k][k] = -diag;
            }
        }
        return L;
    }

    // Conjugate Gradient solver for symmetric positive-definite A
    static double[] solveCG(double[][] A, double[] b, double tol, int maxIter) {
        int n = b.length;
        double[] x = new double[n];
        double[] r = Arrays.copyOf(b, n);
        double[] dir = Arrays.copyOf(r, n);
        double rsold = dot(r, r);

        for (int it = 0; it < maxIter; it++) {
            double[] Adir = matVec(A, dir);
            double alpha = rsold / dot(dir, Adir);
            for (int i = 0; i < n; i++) x[i] += alpha * dir[i];
            for (int i = 0; i < n; i++) r[i] -= alpha * Adir[i];
            double rsnew = dot(r, r);
            if (Math.sqrt(rsnew) < tol) break;
            for (int i = 0; i < n; i++) dir[i] = r[i] + (rsnew / rsold) * dir[i];
            rsold = rsnew;
        }
        return x;
    }

    static double[] matVec(double[][] M, double[] x) {
        int n = x.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) sum += M[i][j] * x[j];
            y[i] = sum;
        }
        return y;
    }

    static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    static int index(int i, int j) {
        return j * Nx + i;
    }
}
