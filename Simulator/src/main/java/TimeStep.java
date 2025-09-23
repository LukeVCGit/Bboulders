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
    private static final double GRID_WIDTH = EPS.L_MAX;
    private static final double CELL_AREA_THRESHOLD = 5;
    private static final double EPS_AREA_THRESHOLD = 0.3;

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
        Vector gridCoords = new Vector(Math.round(particle.position.x() / GRID_WIDTH), Math.round(particle.position.y() / GRID_WIDTH));
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
        Vector gridCoords = new Vector(Math.round(pos.x() / GRID_WIDTH), Math.round(pos.y() / GRID_WIDTH));
        List<EPS> cellContents = grid.get(gridCoords);
        if (cellContents == null)
            cellContents = new ArrayList<>();
        return cellContents;
    }

    public TimeStep next(double dt) {
        List<EPS> newEps = produceEPS(dt);

        var epsStream = eps.parallelStream().map(eps -> {
            var colliders = potentialColliders(eps);
            return eps.tick(dt, colliders).getFirst(); //we can just get first since EPS.tick always returns a list of size 1.
        });
        newEps.addAll(epsStream.toList());

        var newBactStream = bacteria.parallelStream().flatMap(cell -> {
            var colliders = potentialColliders(cell);
            var newBact = (List<Cell>) (List<?>) cell.tick(dt, colliders);
            return newBact.stream();
        });

        return new TimeStep(newEps, newBactStream.toList());
    }

    private List<EPS> produceEPS(double dt) {
        List<EPS> newEps = new ArrayList<>();

        //loop through every grid points
        for (var entry : grid.entrySet()) {
            //calculate the total cell area in each grid cell
            double cellAreaSum = 0;
            double epsAreaSum = 0;
            int cellCount = 0;
            for (var particle : entry.getValue()) {
                if (particle.getClass() == Cell.class) {
                    cellAreaSum += particle.area();
                    cellCount++;
                } else
                    epsAreaSum += particle.area();
            }

            if (cellAreaSum > CELL_AREA_THRESHOLD && epsAreaSum < EPS_AREA_THRESHOLD) { //conditions for eps to be produced
                double rand = ThreadLocalRandom.current().nextDouble();
                double prob = dt * cellCount; //chance that an eps should be produced at any given timestep

                if (rand > prob) //division doesn't occur
                    continue;

                Vector gridCellCentre = entry.getKey().scale(GRID_WIDTH);
                newEps.add(new EPS(gridCellCentre));
            }
        }

        return newEps;
    }

    private List<EPS> potentialColliders(EPS particle) { //todo: optimise with grid
        List<EPS> potentialColliders = new ArrayList<>();
        double xPos = particle.position.x();
        double yPos = particle.position.y();

        for (double x = xPos- GRID_WIDTH; x <= xPos + GRID_WIDTH + 1e-3; x += GRID_WIDTH) {
            for (double y = yPos- GRID_WIDTH; y <= yPos + GRID_WIDTH + 1e-3; y += GRID_WIDTH) {
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
