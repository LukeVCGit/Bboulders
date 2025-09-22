import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class TimeStep {
    private static final ObjectMapper msgpackMapper = new ObjectMapper(new MessagePackFactory());
    private static final ForkJoinPool pool =  new ForkJoinPool();
    private static final List<ForkJoinTask<?>> fileSaveTasks = new ArrayList<>();

    private List<EPS> eps;
    private List<Cell> bacteria;

    public List<EPS> getEps() {
        return eps;
    }

    public List<Cell> getBacteria() {
        return bacteria;
    }

    public TimeStep(List<EPS> eps, List<Cell> bacteria) {
        this.eps = eps;
        this.bacteria = bacteria;
    }

    public TimeStep next(double dt) { //todo parallelise
        List<EPS> newEps = new ArrayList<>();
        for (EPS ep : eps) {
            var colliders = potentialColliders(ep);
            newEps.addAll(ep.tick(dt, colliders));
        }

        //todo EPS production

        List<Cell> newBacteria = new ArrayList<>();
        for (Cell cell : bacteria) {
            var colliders = potentialColliders(cell);
            var newBact = (List<Cell>) (List<?>) cell.tick(dt, colliders);
            newBacteria.addAll(newBact);
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

    private void save(Path dir, int i) {
        Path destFile = dir.resolve(String.format("%05d.boulder", i));

        try {
            byte[] data = msgpackMapper.writeValueAsBytes(this);
            Files.write(destFile, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void startSaveAsync(Path dir, int i) {
        var task = pool.submit(() -> save(dir,i));
        fileSaveTasks.add(task);
    }

    public static void saveFrames() {
        for (var task : fileSaveTasks)
            task.join();
    }
}
