#!/usr/bin/python
import glob, os
import json
from pprint import pprint
import codecs
import sys

data = open('lexicons/roboy-dbpedia.lexicon', 'r')
output_json = open("lexicons/roboy.lexicon", "w")
for record in data.readlines():
    print record
    if ("{" in record):
        lexeme = json.loads(record)
        if lexeme["type"] == "ClassNoun":
            lexeme["formula"] = "(rdf:type " + lexeme["formula"] + ")"
        if lexeme["type"] in ["RelationalNoun","RelationalAdjective","StateVerb","ConsequenceVerb"]:
            json.dump(lexeme, output_json)
            output_json.write("\n")
            lexeme["formula"] = "!" + lexeme["formula"]
        json.dump(lexeme, output_json)
        output_json.write("\n")
    else:
        output_json.write(record)
        output_json.write("\n")