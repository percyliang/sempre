#!/usr/bin/python
import glob, os
import json
from pprint import pprint
import codecs


f = codecs.open('rpqa-train.json', 'r', 'UTF-8')
data = json.load(f)
output = open("rpqa-train.examples", "w")

for record in data:
    print record
    output.write("(example\n")
    output.write("\t(utterance \"" + record["utterance"].encode('utf-8') + "\")\n")
    output.write("\t(targetFormula "  + record["targetFormula"].encode('utf-8') + ")\n)")
    output.write("\n")
