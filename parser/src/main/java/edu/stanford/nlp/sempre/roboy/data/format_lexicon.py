#!/usr/bin/python
import glob, os

os.chdir("./dbpedia")
output = open("people-converted.lexicon", "w")

add_prefix = False

written = 0
unwritten = 0
for file in glob.glob("*.lexicon"):
    input = open(file, "r")
    for line in input:
        line = str(line)
        raw_input(line)
        line.replace("\'","\"")
        raw_input(line)
        output.write(line)
        #print lexeme
