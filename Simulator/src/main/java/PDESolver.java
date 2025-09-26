import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PDESolver {
    static int nx = 200;
    static int ny = 200;
    private final ArrayList<Double> C; // concentration field (flat list)

    // Sparse matrix (CSR for A = I - dt*D*L)
    private int[] rowPtr, colInd;
    private double[] values;
    public PDESolver() { //todo thoroughly check
        this.C = new ArrayList<>(nx * ny);
        for (int i = 0; i < nx * ny; i++) {
            C.add(0.0);
        }

    }

    /** Set uniform initial condition */
    public void setInitialCondition(double value) {
        for (int i = 0; i < C.size(); i++) {
            C.set(i, value);
        }
    }

    /** Return a deep copy of the concentration field */
    public ArrayList<Double> getConcentration() {
        return C;
    }

    /** Advance one timestep using implicit scheme */
    public void solveStep(double dt, List<Cell> cells) {
        int N = nx * ny;

        // --- RHS starts as old concentration (implicit Euler base) ---
        double[] rhs = new double[N];
        for (int idx = 0; idx < N; idx++) {
            rhs[idx] = C.get(idx);
        }

        // --- Apply local sinks ---
        double k = 4.0;

        for (Cell cell : cells) {
            // Map cell's (x,y) to nearest grid index
            int i = (int) Math.round(cell.position.x());
            int j = (int) Math.round(cell.position.y());
            if (i >= 0 && i < nx && j >= 0 && j < ny) {
                int idx = i * ny + j;
                double conc = C.get(idx);
                rhs[idx] -= dt * k * cell.area() * Cell.f(conc);
            }
        }

        // --- Build sparse matrix (can be cached if dt constant) ---
        buildSparseMatrix(dt);

        // --- Solve A x = rhs with CG ---
        double[] newCvec = conjugateGradient(rowPtr, colInd, values, rhs);

        // --- Copy solution back into ArrayList ---
        for (int i = 0; i < N; i++) {
            C.set(i, newCvec[i]);
        }
    }


    /** Build CSR for matrix A = I - dt*D*L */
    private void buildSparseMatrix(double dt) {
        int N = nx * ny;
        double dx = 1;
        double idx2 = 1.0 / (dx * dx);
        double dy = 1;
        double idy2 = 1.0 / (dy * dy);

        rowPtr = new int[N + 1];
        colInd = new int[N * 5];
        values = new double[N * 5];

        int nnz = 0;
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                int idx = i * ny + j;
                rowPtr[idx] = nnz;

                double diag = 1.0;

                double d = 1e-4;
                if (i > 0) { // left
                    colInd[nnz] = (i - 1) * ny + j;
                    values[nnz++] = -dt * d * idx2;
                    diag += dt * d * idx2;
                }
                if (i < nx - 1) { // right
                    colInd[nnz] = (i + 1) * ny + j;
                    values[nnz++] = -dt * d * idx2;
                    diag += dt * d * idx2;
                }
                if (j > 0) { // down
                    colInd[nnz] = i * ny + (j - 1);
                    values[nnz++] = -dt * d * idy2;
                    diag += dt * d * idy2;
                }
                if (j < ny - 1) { // up
                    colInd[nnz] = i * ny + (j + 1);
                    values[nnz++] = -dt * d * idy2;
                    diag += dt * d * idy2;
                }

                // diagonal
                colInd[nnz] = idx;
                values[nnz++] = diag;
            }
        }
        rowPtr[N] = nnz;

        colInd = Arrays.copyOf(colInd, nnz);
        values = Arrays.copyOf(values, nnz);
    }

    /** Conjugate Gradient solver for symmetric positive definite A */
    private double[] conjugateGradient(int[] rowPtr, int[] colInd, double[] values,
                                       double[] b) {
        int N = b.length;
        double[] x = new double[N]; // initial guess = 0
        double[] r = Arrays.copyOf(b, N);
        double[] p = Arrays.copyOf(r, N);
        double rsold = dot(r, r);

        for (int iter = 0; iter < 10000; iter++) {
            double[] Ap = matVec(rowPtr, colInd, values, p);
            double alpha = rsold / dot(p, Ap);

            for (int i = 0; i < N; i++) {
                x[i] += alpha * p[i];
                r[i] -= alpha * Ap[i];
            }

            double rsnew = dot(r, r);
            if (Math.sqrt(rsnew) < 1.0E-8) break;

            double beta = rsnew / rsold;
            for (int i = 0; i < N; i++) {
                p[i] = r[i] + beta * p[i];
            }
            rsold = rsnew;
        }
        return x;
    }

    /** Sparse matrix-vector product in CSR format */
    private double[] matVec(int[] rowPtr, int[] colInd, double[] values, double[] x) {
        int N = x.length;
        double[] y = new double[N];
        for (int i = 0; i < N; i++) {
            double sum = 0.0;
            for (int p = rowPtr[i]; p < rowPtr[i + 1]; p++) {
                sum += values[p] * x[colInd[p]];
            }
            y[i] = sum;
        }
        return y;
    }

    private double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }
}
