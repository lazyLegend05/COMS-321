import sys
from typing import Dict, List


def sign_extend(value: int, bits: int) -> int:
    """Return the two's‑complement sign‑extended value of ``value`` with ``bits`` bits.

    >>> sign_extend(0b1111, 4)
    -1
    >>> sign_extend(0b0010, 4)
    2
    """
    sign_bit = 1 << (bits - 1)
    mask = (1 << bits) - 1
    value &= mask
    return (value ^ sign_bit) - sign_bit


def get_register_name(num: int) -> str:
    """Return the textual name of register ``num``.
    """
    if num == 31:
        return "XZR"
    return f"X{num}"


def decode_instructions(words: List[int]) -> List[str]:
    """Disassemble a list of 32‑bit instruction words."""
    # Mapping from instruction index to label name
    label_map: Dict[int, str] = {}
    label_counter = 0

    # Condition code names for B.cond (lower four bits of Rt field)
    cond_names = {
        0x0: "EQ",
        0x1: "NE",
        0x2: "HS",
        0x3: "LO",
        0x4: "MI",
        0x5: "PL",
        0x6: "VS",
        0x7: "VC",
        0x8: "HI",
        0x9: "LS",
        0xA: "GE",
        0xB: "LT",
        0xC: "GT",
        0xD: "LE",
    }

    # First pass: determine branch targets and assign labels
    for idx, inst in enumerate(words):
        op6 = (inst >> 26) & 0x3F
        # B and BL use a 26‑bit immediate (shifted left by 2)
        if op6 == 0b000101 or op6 == 0b100101:
            imm26 = inst & 0x03FFFFFF  # 26 bits
            offset = sign_extend(imm26, 26)
            target = idx + offset
            if 0 <= target < len(words):
                if target not in label_map:
                    label_counter += 1
                    label_map[target] = f"label{label_counter}"
            continue
        op8 = (inst >> 24) & 0xFF
        # B.cond, CBZ and CBNZ use a 19‑bit immediate (shifted left by 2)
        if op8 in (0b01010100, 0b10110100, 0b10110101):
            imm19 = (inst >> 5) & 0x7FFFF  # bits 23–5
            offset = sign_extend(imm19, 19)
            target = idx + offset
            if 0 <= target < len(words):
                if target not in label_map:
                    label_counter += 1
                    label_map[target] = f"label{label_counter}"
            continue
        # Other instructions do not generate labels

    lines: List[str] = []
    # Second pass: disassemble each instruction and insert labels
    for idx, inst in enumerate(words):
        # Insert label if this instruction is a branch target
        if idx in label_map:
            lines.append(f"{label_map[idx]}:")

        op6 = (inst >> 26) & 0x3F
        # Handle unconditional branch (B)
        if op6 == 0b000101:
            imm26 = inst & 0x03FFFFFF
            offset = sign_extend(imm26, 26)
            target = idx + offset
            label = label_map.get(target, f"#addr{target}")
            lines.append(f"    B {label}")
            continue
        # Handle branch with link (BL)
        if op6 == 0b100101:
            imm26 = inst & 0x03FFFFFF
            offset = sign_extend(imm26, 26)
            target = idx + offset
            label = label_map.get(target, f"#addr{target}")
            lines.append(f"    BL {label}")
            continue

        op8 = (inst >> 24) & 0xFF
        # Handle conditional branch (B.cond)
        if op8 == 0b01010100:
            imm19 = (inst >> 5) & 0x7FFFF
            offset = sign_extend(imm19, 19)
            target = idx + offset
            cond_code = inst & 0xF
            cond = cond_names.get(cond_code, f"0x{cond_code:X}")
            label = label_map.get(target, f"#addr{target}")
            lines.append(f"    B.{cond} {label}")
            continue
        # Handle compare and branch if zero (CBZ)
        if op8 == 0b10110100:
            imm19 = (inst >> 5) & 0x7FFFF
            offset = sign_extend(imm19, 19)
            target = idx + offset
            rt = inst & 0x1F
            rt_name = get_register_name(rt)
            label = label_map.get(target, f"#addr{target}")
            lines.append(f"    CBZ {rt_name}, {label}")
            continue
        # Handle compare and branch if not zero (CBNZ)
        if op8 == 0b10110101:
            imm19 = (inst >> 5) & 0x7FFFF
            offset = sign_extend(imm19, 19)
            target = idx + offset
            rt = inst & 0x1F
            rt_name = get_register_name(rt)
            label = label_map.get(target, f"#addr{target}")
            lines.append(f"    CBNZ {rt_name}, {label}")
            continue

        # Extract larger opcodes for R/I/D‑format decoding
        op11 = (inst >> 21) & 0x7FF
        op10 = (inst >> 22) & 0x3FF

        # D‑format load/store
        if op11 == 0b11111000010:  # LDUR
            imm9 = (inst >> 12) & 0x1FF
            offset = sign_extend(imm9, 9)
            rn = (inst >> 5) & 0x1F
            rt = inst & 0x1F
            rn_name = get_register_name(rn)
            rt_name = get_register_name(rt)
            lines.append(f"    LDUR {rt_name}, [{rn_name}, #{offset}]")
            continue
        if op11 == 0b11111000000:  # STUR
            imm9 = (inst >> 12) & 0x1FF
            offset = sign_extend(imm9, 9)
            rn = (inst >> 5) & 0x1F
            rt = inst & 0x1F
            rn_name = get_register_name(rn)
            rt_name = get_register_name(rt)
            lines.append(f"    STUR {rt_name}, [{rn_name}, #{offset}]")
            continue

        # BR (branch to register)
        if op11 == 0b11010110000:
            rn = (inst >> 5) & 0x1F
            rn_name = get_register_name(rn)
            lines.append(f"    BR {rn_name}")
            continue

        # Special emulator instructions
        if op11 == 0b11111111101:  # PRNT
            rd = inst & 0x1F
            rd_name = get_register_name(rd)
            lines.append(f"    PRNT {rd_name}")
            continue
        if op11 == 0b11111111100:  # PRNL
            lines.append(f"    PRNL")
            continue
        if op11 == 0b11111111110:  # DUMP
            lines.append(f"    DUMP")
            continue
        if op11 == 0b11111111111:  # HALT
            lines.append(f"    HALT")
            continue

        # R‑format arithmetic/logical instructions
        if op11 == 0b10001011000:  # ADD
            rm = (inst >> 16) & 0x1F
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    ADD {get_register_name(rd)}, {get_register_name(rn)}, {get_register_name(rm)}"
            )
            continue
        if op11 == 0b11001011000:  # SUB
            rm = (inst >> 16) & 0x1F
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    SUB {get_register_name(rd)}, {get_register_name(rn)}, {get_register_name(rm)}"
            )
            continue
        if op11 == 0b11101011000:  # SUBS
            rm = (inst >> 16) & 0x1F
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    SUBS {get_register_name(rd)}, {get_register_name(rn)}, {get_register_name(rm)}"
            )
            continue
        if op11 == 0b10001010000:  # AND
            rm = (inst >> 16) & 0x1F
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    AND {get_register_name(rd)}, {get_register_name(rn)}, {get_register_name(rm)}"
            )
            continue
        if op11 == 0b10101010000:  # ORR
            rm = (inst >> 16) & 0x1F
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    ORR {get_register_name(rd)}, {get_register_name(rn)}, {get_register_name(rm)}"
            )
            continue
        if op11 == 0b11001010000:  # EOR
            rm = (inst >> 16) & 0x1F
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    EOR {get_register_name(rd)}, {get_register_name(rn)}, {get_register_name(rm)}"
            )
            continue
        if op11 == 0b10011011000:  # MUL
            rm = (inst >> 16) & 0x1F
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    MUL {get_register_name(rd)}, {get_register_name(rn)}, {get_register_name(rm)}"
            )
            continue
        if op11 == 0b11010011011:  # LSL
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    LSL {get_register_name(rd)}, {get_register_name(rn)}, #{shamt}"
            )
            continue
        if op11 == 0b11010011010:  # LSR
            shamt = (inst >> 10) & 0x3F
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    LSR {get_register_name(rd)}, {get_register_name(rn)}, #{shamt}"
            )
            continue

        # I‑format arithmetic/logical instructions
        if op10 == 0b1001000100:  # ADDI
            imm12 = (inst >> 10) & 0xFFF
            offset = sign_extend(imm12, 12)
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    ADDI {get_register_name(rd)}, {get_register_name(rn)}, #{offset}"
            )
            continue
        if op10 == 0b1001001000:  # ANDI
            imm12 = (inst >> 10) & 0xFFF
            offset = sign_extend(imm12, 12)
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    ANDI {get_register_name(rd)}, {get_register_name(rn)}, #{offset}"
            )
            continue
        if op10 == 0b1101001000:  # EORI
            imm12 = (inst >> 10) & 0xFFF
            offset = sign_extend(imm12, 12)
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    EORI {get_register_name(rd)}, {get_register_name(rn)}, #{offset}"
            )
            continue
        if op10 == 0b1011001000:  # ORRI
            imm12 = (inst >> 10) & 0xFFF
            offset = sign_extend(imm12, 12)
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    ORRI {get_register_name(rd)}, {get_register_name(rn)}, #{offset}"
            )
            continue
        if op10 == 0b1101000100:  # SUBI
            imm12 = (inst >> 10) & 0xFFF
            offset = sign_extend(imm12, 12)
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    SUBI {get_register_name(rd)}, {get_register_name(rn)}, #{offset}"
            )
            continue
        if op10 == 0b1111000100:  # SUBIS
            imm12 = (inst >> 10) & 0xFFF
            offset = sign_extend(imm12, 12)
            rn = (inst >> 5) & 0x1F
            rd = inst & 0x1F
            lines.append(
                f"    SUBIS {get_register_name(rd)}, {get_register_name(rn)}, #{offset}"
            )
            continue

        # Unknown instruction; emit word as a comment or raw value
        # Display the 32‑bit value in hex for debugging
        lines.append(f"    .word 0x{inst:08X}")

    return lines


def main(argv: List[str]) -> int:
    if len(argv) < 2:
        sys.stderr.write("Usage: parser.py <binary file>\n")
        return 1
    path = argv[1]
    try:
        with open(path, "rb") as f:
            data = f.read()
    except IOError as e:
        sys.stderr.write(f"Error reading {path}: {e}\n")
        return 1
    if len(data) % 4 != 0:
        sys.stderr.write("Error: input file size is not a multiple of 4 bytes\n")
        return 1
    # Convert bytes to 32‑bit words
    words = []
    for i in range(0, len(data), 4):
        word = int.from_bytes(data[i:i + 4], byteorder="big", signed=False)
        words.append(word)
    lines = decode_instructions(words)
    for line in lines:
        print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))