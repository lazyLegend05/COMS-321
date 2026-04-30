ADDI  X0, XZR, #0

start:
        ADDI  X1, X0, #1
        B     skip

        ADDI  X2, X0, #2

skip:
        BL    target
        BR    X30

        HALT

target:
        ADDI  X3, X0, #3
        BR    X30

