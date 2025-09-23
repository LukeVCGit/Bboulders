import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {
        int fps = 120;
        int secToRunFor = 15;

        System.out.println("Running");

        Simulation.run(fps, secToRunFor);
    }
}
