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
            int currentAddress = 0; 
            for (int inst : instructions) {
                InstructionData data = extract(inst);

                // TODO: use data and currentAddress to calc targets (currentAddress + data.address * 4) and print

                // For testing with assignment 1
                // System.out.printf("ADDR %d: [%s] Op: %x | Rd/Rt: %d | Rn: %d | Rm: %d | Imm: %d | Addr: %d%n", 
                //     currentAddress, data.format, data.opcode, data.rd, data.rn, data.rm, data.imm, data.address);

                currentAddress += 4; 
            }

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

    static InstructionData extract(int inst) {
        InstructionData d = new InstructionData();

        // opcodes
        int op6  = (inst >>> 26) & 0x3F;
        int op8  = (inst >>> 24) & 0xFF;
        int op10 = (inst >>> 22) & 0x3FF;
        int op11 = (inst >>> 21) & 0x7FF;

        // B, BL
        if (op6 == 0x05 || op6 == 0x25) {
            d.format = "B";
            d.opcode = op6;
            int off = inst & 0x3FFFFFF;
            if ((off & 0x2000000) != 0) off |= 0xFC000000;
            d.address = off;
        } 
        // B.cond, CBZ, CBNZ
        else if (op8 == 0x54 || op8 == 0xB4 || op8 == 0xB5) {
            d.format = "CB";
            d.opcode = op8;
            d.rt = inst & 0x1F;
            d.cond = inst & 0x1F;
            int off = (inst >>> 5) & 0x7FFFF;
            if ((off & 0x40000) != 0) off |= 0xFFF80000;
            d.address = off;
        }
        // ADDI, ANDI, etc
        else if (op10 == 0x244 || op10 == 0x2C4 || op10 == 0x344 || op10 == 0x3C4 || 
                op10 == 0x248 || op10 == 0x2C8 || op10 == 0x348 || op10 == 0x3C8) {
            d.format = "I";
            d.opcode = op10;
            d.rd = inst & 0x1F;
            d.rn = (inst >>> 5) & 0x1F;
            d.imm = (inst >>> 10) & 0xFFF;
        }
        // 4. R and D format
        else {
            d.opcode = op11;
            d.rd = inst & 0x1F;
            d.rt = inst & 0x1F;
            d.rn = (inst >>> 5) & 0x1F;
            d.rm = (inst >>> 16) & 0x1F;
            d.shamt = (inst >>> 10) & 0x3F;

            // LDUR, STUR, etc
            if (op8 == 0x38 || op11 == 0x7C2 || op11 == 0x7C0 || (op11 & 0x7F0) == 0x7C0) {
                d.format = "D";
                int off = (inst >>> 12) & 0x1FF;
                if ((off & 0x100) != 0) off |= 0xFFFFFE00;
                d.address = off;
            } else {
                d.format = "R";
            }
        }
        return d;
    }
}

class InstructionData {
    String format; 
    int opcode;
    int rd, rn, rm, rt;
    int imm;
    int address; 
    int shamt;
    int cond;
}