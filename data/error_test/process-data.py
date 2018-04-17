#!/usr/bin/python
import glob, os
import json
from pprint import pprint
import codecs
import sys

path = "./relations"
output = open("relations.json", "w")

output.write("[")
for filename in os.listdir(path):
    input = open(path + "/" + filename, "r")
    content = input.readlines()
    for line in content:
        dict = {}
        print line
        dict["lexeme"]=line[1:line.index(";")-1]
        dict["formula"]=line[line.index(";")+1:]
        dict["type"]="Property";
        output.write(str(dict))
        output.write(",\n")
output.write("]")
