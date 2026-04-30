#!/bin/sh
# Run script for the LEGv8 disassembler

# Pass the name of the binary file to the Python disassembler.
python3 parser.py "$1"