import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Incorrect number of arguments, instead use <secToRunFor> <simSize> <motility>");
            System.out.println("secToRunFor: number of seconds to run the simulation for");
            System.out.println("simSize: maximum area that the cells can inhabit e.g 300");
            System.out.println("motility: the motility force of each cell e.g. 200, 700");
            return;
        }

        try {
            int secToRunFor = Integer.parseInt(args[0]);
            int simSize = Integer.parseInt(args[1]);
            double motility = Double.parseDouble(args[2]);
            Simulation.run(secToRunFor, simSize, motility);
        }
        catch (NumberFormatException e) {
            System.out.println("Unable to parse arguments, run the program again without any arguments to view their format");
        }
    }
}
