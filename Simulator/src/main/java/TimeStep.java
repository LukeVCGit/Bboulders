import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;

public class TimeStep {
    private static final Gson gson = new Gson();

    private List<EPS> eps;
    private List<Cell> bacteria;

    public TimeStep(List<EPS> eps, List<Cell> bacteria) {
        this.eps = eps;
        this.bacteria = bacteria;
    }

    public TimeStep next(double dt) {
        List<EPS> newEps = new ArrayList<>();
        for (EPS ep : eps) {
            var colliders = potentialColliders(ep);
            newEps.addAll(ep.tick(dt, colliders));
        }

        List<Cell> newBacteria = new ArrayList<>();
        for (Cell cell : bacteria) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        return new TimeStep(newEps, newBacteria);
    }

    private List<EPS> potentialColliders(EPS particle) { //todo: optimise with grid
        List<EPS> potentialColliders = new ArrayList<>();

        for (EPS ep : eps) { //check if any EPS particles are close enough
            if (ep.position.equals(particle.position)) //valid collisions are not with oneself
                continue;

            double dist = ep.position.subtract(particle.position).mag();
            if (dist <= 0.5*(EPS.d_eps + EPS.L_MAX)) //particle might be a cell of maximum length
                potentialColliders.add(ep);
        }

        for (Cell cell : bacteria) { //check if any cells are close enough
            if (cell.position.equals(particle.position)) //valid collisions are not with oneself
                continue;

            double dist = cell.position.subtract(particle.position).mag();
            if (dist <= EPS.L_MAX) //particle might be a cell of maximum length
                potentialColliders.add(cell);
        }

        return potentialColliders;
    }

    public void save(Path dir, int i) {
        String data = gson.toJson(this);
        Path destFile = dir.resolve(String.format("%05d.json", i));

        try { //java bloat
            Files.writeString(destFile, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
