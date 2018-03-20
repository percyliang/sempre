#!/usr/bin/python
import glob, os
import json
from pprint import pprint
import codecs


f = codecs.open('webq-train.json', 'r', 'UTF-8')
data = json.load(f)
output = open("webq-train.examples", "w")

for record in data:
    print record
    output.write("(example\n")
    output.write("\t(utterance \"" + record["utterance"].encode('utf-8').replace('"','\\"') + "\")\n")
    output.write("\t(targetValue "  + record["targetValue"].encode('utf-8').replace('"','\\"') + ")\n)")
    output.write("\n")
    print output
