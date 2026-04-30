import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Disasm {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: java Disasm <LEGv8 binary file>");
            System.exit(2);
        }

        try {
            List<Integer> instructions = readProgram(args[0]);

        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    static List<Integer> readProgram(String filename) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(filename));

        if (data.length % 4 != 0) {
            throw new IOException("input size is not a multiple of 4 bytes");
        }

        List<Integer> instructions = new ArrayList<>();

        for (int i = 0; i < data.length; i += 4) {
            int inst =
                    ((data[i] & 0xFF) << 24) |
                            ((data[i + 1] & 0xFF) << 16) |
                            ((data[i + 2] & 0xFF) << 8) |
                            (data[i + 3] & 0xFF);

            instructions.add(inst);
        }

        return instructions;
    }
}