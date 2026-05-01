import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Disasm {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: java Disasm <LEGv8 binary file>");
            System.exit(2);
        }

        try {
            List<Integer> instructions = readProgram(args[0]);

            Map<Integer, String> labels = buildLabelMap(instructions);

            for (int i = 0; i < instructions.size(); i++) {
                if (labels.containsKey(i)) {
                    System.out.println(labels.get(i) + ":");
                }

                String line = formatInstruction(instructions.get(i), i, labels);
                System.out.println("    " + line);
            }

        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    /*
     * Person 1 part:
     * Read binary file.
     * Every 4 bytes become one 32-bit instruction.
     * Input is big-endian, so byte[0] is the most significant byte.
     */
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

    /*
     * Person 3 important part:
     * First pass over the program.
     * Find all branch targets and assign generated labels.
     */
    static Map<Integer, String> buildLabelMap(List<Integer> instructions) {
        Map<Integer, String> labels = new LinkedHashMap<>();
        int labelNum = 1;

        for (int i = 0; i < instructions.size(); i++) {
            int inst = instructions.get(i);
            Integer target = getBranchTargetIndex(inst, i);

            if (target != null && target >= 0 && target < instructions.size()) {
                if (!labels.containsKey(target)) {
                    labels.put(target, "label" + labelNum);
                    labelNum++;
                }
            }
        }

        return labels;
    }

    /*
     * Returns the target instruction index for branch instructions.
     * Returns null if the instruction is not a branch that uses a label.
     */
    static Integer getBranchTargetIndex(int inst, int currentIndex) {
        int op6 = (inst >>> 26) & 0x3F;
        int op8 = (inst >>> 24) & 0xFF;

        // B and BL use signed 26-bit instruction offset.
        if (op6 == 0x05 || op6 == 0x25) {
            int imm26 = inst & 0x03FFFFFF;
            int offset = signExtend(imm26, 26);
            return currentIndex + offset;
        }

        // B.cond, CBZ, and CBNZ use signed 19-bit instruction offset.
        if (op8 == 0x54 || op8 == 0xB4 || op8 == 0xB5) {
            int imm19 = (inst >>> 5) & 0x7FFFF;
            int offset = signExtend(imm19, 19);
            return currentIndex + offset;
        }

        return null;
    }

    /*
     * Person 2 + Person 3 combined:
     * Decode the instruction fields and format the final LEGv8 assembly line.
     */
    static String formatInstruction(int inst, int currentIndex, Map<Integer, String> labels) {
        int op6 = (inst >>> 26) & 0x3F;
        int op8 = (inst >>> 24) & 0xFF;
        int op10 = (inst >>> 22) & 0x3FF;
        int op11 = (inst >>> 21) & 0x7FF;

        /*
         * B-format instructions
         */
        if (op6 == 0x05) {
            int imm26 = inst & 0x03FFFFFF;
            int offset = signExtend(imm26, 26);
            int target = currentIndex + offset;
            return "B " + labelOrFallback(labels, target, offset);
        }

        if (op6 == 0x25) {
            int imm26 = inst & 0x03FFFFFF;
            int offset = signExtend(imm26, 26);
            int target = currentIndex + offset;
            return "BL " + labelOrFallback(labels, target, offset);
        }

        /*
         * CB-format instructions
         */
        if (op8 == 0x54) {
            int imm19 = (inst >>> 5) & 0x7FFFF;
            int offset = signExtend(imm19, 19);
            int target = currentIndex + offset;
            int condCode = inst & 0xF;

            return "B." + conditionName(condCode) + " " + labelOrFallback(labels, target, offset);
        }

        if (op8 == 0xB4) {
            int imm19 = (inst >>> 5) & 0x7FFFF;
            int offset = signExtend(imm19, 19);
            int target = currentIndex + offset;
            int rt = inst & 0x1F;

            return "CBZ " + reg(rt) + ", " + labelOrFallback(labels, target, offset);
        }

        if (op8 == 0xB5) {
            int imm19 = (inst >>> 5) & 0x7FFFF;
            int offset = signExtend(imm19, 19);
            int target = currentIndex + offset;
            int rt = inst & 0x1F;

            return "CBNZ " + reg(rt) + ", " + labelOrFallback(labels, target, offset);
        }

        /*
         * D-format instructions
         */
        if (op11 == 0x7C2) {
            int rt = inst & 0x1F;
            int rn = (inst >>> 5) & 0x1F;
            int address = signExtend((inst >>> 12) & 0x1FF, 9);

            return "LDUR " + reg(rt) + ", [" + reg(rn) + ", #" + address + "]";
        }

        if (op11 == 0x7C0) {
            int rt = inst & 0x1F;
            int rn = (inst >>> 5) & 0x1F;
            int address = signExtend((inst >>> 12) & 0x1FF, 9);

            return "STUR " + reg(rt) + ", [" + reg(rn) + ", #" + address + "]";
        }

        /*
         * BR instruction
         */
        if (op11 == 0x6B0) {
            int rn = (inst >>> 5) & 0x1F;
            return "BR " + reg(rn);
        }

        /*
         * Special emulator instructions
         */
        if (op11 == 0x7FD) {
            int rd = inst & 0x1F;
            return "PRNT " + reg(rd);
        }

        if (op11 == 0x7FC) {
            return "PRNL";
        }

        if (op11 == 0x7FE) {
            return "DUMP";
        }

        if (op11 == 0x7FF) {
            return "HALT";
        }

        /*
         * R-format instructions
         */
        if (op11 == 0x458) {
            return formatR("ADD", inst);
        }

        if (op11 == 0x658) {
            return formatR("SUB", inst);
        }

        if (op11 == 0x758) {
            return formatR("SUBS", inst);
        }

        if (op11 == 0x450) {
            return formatR("AND", inst);
        }

        if (op11 == 0x550) {
            return formatR("ORR", inst);
        }

        if (op11 == 0x650) {
            return formatR("EOR", inst);
        }

        if (op11 == 0x4D8) {
            return formatR("MUL", inst);
        }

        /*
         * Shift instructions
         */
        if (op11 == 0x69B) {
            int rd = inst & 0x1F;
            int rn = (inst >>> 5) & 0x1F;
            int shamt = (inst >>> 10) & 0x3F;

            return "LSL " + reg(rd) + ", " + reg(rn) + ", #" + shamt;
        }

        if (op11 == 0x69A) {
            int rd = inst & 0x1F;
            int rn = (inst >>> 5) & 0x1F;
            int shamt = (inst >>> 10) & 0x3F;

            return "LSR " + reg(rd) + ", " + reg(rn) + ", #" + shamt;
        }

        /*
         * I-format instructions
         * Immediate is 12-bit unsigned for these supported LEGv8 I-format instructions.
         */
        if (op10 == 0x244) {
            return formatI("ADDI", inst);
        }

        if (op10 == 0x344) {
            return formatI("SUBI", inst);
        }

        if (op10 == 0x3C4) {
            return formatI("SUBIS", inst);
        }

        if (op10 == 0x248) {
            return formatI("ANDI", inst);
        }

        if (op10 == 0x2C8) {
            return formatI("ORRI", inst);
        }

        if (op10 == 0x348) {
            return formatI("EORI", inst);
        }

        /*
         * Should not happen if input only contains supported assignment instructions.
         */
        return ".word 0x" + String.format("%08X", inst);
    }

    static String formatR(String mnemonic, int inst) {
        int rd = inst & 0x1F;
        int rn = (inst >>> 5) & 0x1F;
        int rm = (inst >>> 16) & 0x1F;

        return mnemonic + " " + reg(rd) + ", " + reg(rn) + ", " + reg(rm);
    }

    static String formatI(String mnemonic, int inst) {
        int rd = inst & 0x1F;
        int rn = (inst >>> 5) & 0x1F;
        int imm = (inst >>> 10) & 0xFFF;

        return mnemonic + " " + reg(rd) + ", " + reg(rn) + ", #" + imm;
    }

    static String reg(int num) {
        if (num == 31) {
            return "XZR";
        }

        return "X" + num;
    }

    static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }

    static String labelOrFallback(Map<Integer, String> labels, int target, int offset) {
        if (labels.containsKey(target)) {
            return labels.get(target);
        }

        // This fallback should rarely be used for valid assignment inputs.
        // It exists so the disassembler still prints something understandable.
        return "#" + offset;
    }

    static String conditionName(int condCode) {
        switch (condCode) {
            case 0x0:
                return "EQ";
            case 0x1:
                return "NE";
            case 0x2:
                return "HS";
            case 0x3:
                return "LO";
            case 0x4:
                return "MI";
            case 0x5:
                return "PL";
            case 0x6:
                return "VS";
            case 0x7:
                return "VC";
            case 0x8:
                return "HI";
            case 0x9:
                return "LS";
            case 0xA:
                return "GE";
            case 0xB:
                return "LT";
            case 0xC:
                return "GT";
            case 0xD:
                return "LE";
            default:
                return "UNKNOWN";
        }
    }
}