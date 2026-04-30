ADDI  X0, XZR, #0
        ADDI  X1, XZR, #1

        CBZ   X0, if_zero
        CBNZ  X1, if_not_zero

        B     done

if_zero:
        ADDI  X2, X2, #10

if_not_zero:
        ADDI  X3, X3, #20

        B.EQ  finish
        B.NE  finish

done:
        HALT

finish:
        HALT

