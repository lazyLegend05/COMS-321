ADDI  X0, XZR, #0
        ADDI  X1, XZR, #10
        ADDI  X2, XZR, #1

        LSL   X3, X1, #1
        ORR   X4, X3, X2
        AND   X5, X4, X1
        EOR   X6, X4, X5
        MUL   X7, X6, X2

loop:
        SUBIS X1, X1, #1
        STUR  X1, [X0, #8]
        CBNZ  X1, loop
        B.GE  end

        ADDI  X8, XZR, #-1

end:
        PRNT  X1
        PRNL
        HALT

