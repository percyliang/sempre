#!/usr/bin/python
import glob, os
import json
from pprint import pprint
import codecs


f = codecs.open('rpqa-train.json', 'r', 'UTF-8')
data = json.load(f)
output = open("rpqa-train-a.examples", "w")
output_json = open("rpqa-train-a.json", "w")

output_json.write("[\n")
for record in data:
    print record
    if record["type"][0] != 'Q':
        output.write("(example\n")
        output.write("\t(utterance \"" + record["utterance"].encode('utf-8') + "\")\n")
        output.write("\t(targetFormula "  + record["targetFormula"].encode('utf-8') + ")\n)")
        output.write("\n")
        json.dump(record, output_json)
        output_json.write(",\n")

output_json.write("\n]")
