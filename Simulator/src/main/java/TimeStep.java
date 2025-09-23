import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class TimeStep {
    private static final ObjectMapper msgpackMapper = new ObjectMapper(new MessagePackFactory());
    private static final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private static final List<Future<?>> ioTasks = new ArrayList<>();
    private static final double gridWidth = EPS.L_MAX;

    private List<EPS> eps;
    private List<Cell> bacteria;
    private transient HashMap<Vector, List<EPS>> grid = new HashMap<>();

    public List<EPS> getEps() {
        return eps;
    }

    public List<Cell> getBacteria() {
        return bacteria;
    }

    public TimeStep(List<EPS> eps, List<Cell> bacteria) {
        this.eps = eps;
        this.bacteria = bacteria;

        //setup grid
        for (EPS ep : eps)
            addToGrid(ep);
        for (Cell c  : bacteria)
            addToGrid(c);
    }

    private void addToGrid(EPS particle) {
        Vector gridCoords = new Vector(Math.round(particle.position.x() / gridWidth), Math.round(particle.position.y() / gridWidth));
        List<EPS> cellContents = grid.get(gridCoords);
        if (cellContents == null) {
            cellContents = new ArrayList<>();
            cellContents.add(particle);
            grid.put(gridCoords, cellContents);
        } else {
            cellContents.add(particle);
        }
    }

    private List<EPS> getParticlesAtPosition(Vector pos) {
        Vector gridCoords = new Vector(Math.round(pos.x() / gridWidth), Math.round(pos.y() / gridWidth));
        List<EPS> cellContents = grid.get(gridCoords);
        if (cellContents == null)
            cellContents = new ArrayList<>();
        return cellContents;
    }

    public TimeStep next(double dt) { //todo parallelise
        //todo EPS production

        var newEpsStream = eps.parallelStream().map(eps -> {
            var colliders = potentialColliders(eps);
            return eps.tick(dt, colliders).getFirst(); //we can just get first since EPS.tick always returns a list of size 1.
        });

        var newBactStream = bacteria.parallelStream().flatMap(cell -> {
            var colliders = potentialColliders(cell);
            var newBact = (List<Cell>) (List<?>) cell.tick(dt, colliders);
            return newBact.stream();
        });

        return new TimeStep(newEpsStream.toList(), newBactStream.toList());
    }

    private List<EPS> potentialColliders(EPS particle) { //todo: optimise with grid
        List<EPS> potentialColliders = new ArrayList<>();
        double xPos = particle.position.x();
        double yPos = particle.position.y();

        for (double x = xPos-gridWidth; x <= xPos + gridWidth + 1e-3; x += gridWidth) {
            for (double y = yPos-gridWidth; y <= yPos + gridWidth + 1e-3; y += gridWidth) {
                potentialColliders.addAll(getParticlesAtPosition(new Vector(x,y)));
            }
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
        var ioTask = ioExecutor.submit(() -> save(dir, i));
        ioTasks.add(ioTask);
    }

    public static void saveFrames() {
        for (var task : ioTasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        ioExecutor.shutdown();
    }
}
