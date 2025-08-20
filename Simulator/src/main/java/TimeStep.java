import java.util.ArrayList;
import java.util.List;

public class TimeStep {
    private List<EPS> eps = new ArrayList<>();
    private List<Cell> bacteria = new ArrayList<>();

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
        for (Cell bact : bacteria) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        return new TimeStep(newEps, newBacteria);
    }

    private List<EPS> potentialColliders(EPS particle) { //todo optimise with grid
        List<EPS> potentialColliders = new ArrayList<>();

        for (EPS ep : eps) { //check if any EPS particles are close enough
            if (ep.position.equals(particle.position)) //valid collision are not with oneself
                continue;

            double dist = ep.position.subtract(particle.position).mag();
            if (dist <= 0.5*(EPS.d_eps + EPS.L_MAX)) //particle might be a cell of maximum length
                potentialColliders.add(ep);
        }

        for (Cell cell : bacteria) { //check if any cells are close enough
            if (cell.position.equals(particle.position)) //valid collision are not with oneself
                continue;

            double dist = cell.position.subtract(particle.position).mag();
            if (dist <= EPS.L_MAX) //particle might be a cell of maximum length
                potentialColliders.add(cell);
        }

        return potentialColliders;
    }
}
