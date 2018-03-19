#!/usr/bin/python
import glob, os
import json
from pprint import pprint
import codecs
import sys
from random import shuffle

type = raw_input("[q]uestions or [a]nswers or other for all\n")
k = int(raw_input("Choose [k]-fold\n"))
if str(type) == "q":
    f = codecs.open('rpqa-train-q.json', 'r', 'UTF-8')
    data = json.load(f)
    shuffle(data)
    print len(data), k
    for j in range(k):
        output = open("Q/Qtrain-"+str(j)+".examples", "w")
        output_json = open("Q/Qtrain-"+str(j)+".json", "w")
        output_json.write("[\n")
        for i in range(len(data)/k):
            output.write("(example\n")
            output.write("\t(utterance \"" + data[j*k+i]["utterance"].encode('utf-8') + "\")\n")
            output.write("\t(targetFormula "  + data[j*k+i]["targetFormula"].encode('utf-8') + ")\n)")
            output.write("\n")
            json.dump(data[j*k+i], output_json)
            if i < len(data)/k-1:
                output_json.write(",\n")
        output_json.write("\n]")
elif str(type) == "a":
    f = codecs.open('rpqa-train-a.json', 'r', 'UTF-8')
    data = json.load(f)
    shuffle(data)
    print len(data), k
    for j in range(k):
        output = open("A/Atrain-"+str(j)+".examples", "w")
        output_json = open("A/Atrain-"+str(j)+".json", "w")
        output_json.write("[\n")
        for i in range(len(data)/k):
            output.write("(example\n")
            output.write("\t(utterance \"" + data[j*k+i]["utterance"].encode('utf-8') + "\")\n")
            output.write("\t(targetFormula "  + data[j*k+i]["targetFormula"].encode('utf-8') + ")\n)")
            output.write("\n")
            json.dump(data[j*k+i], output_json)
            if i < len(data)/k-1:
                output_json.write(",\n")
        output_json.write("\n]")
else:
    f = codecs.open('rpqa-train.json', 'r', 'UTF-8')
    data = json.load(f)
    shuffle(data)
    print len(data), k
    for j in range(k):
        output = open("train-"+str(j)+".examples", "w")
        output_json = open("train-"+str(j)+".json", "w")
        output_json.write("[\n")
        for i in range(len(data)/k):
            output.write("(example\n")
            output.write("\t(utterance \"" + data[j*k+i]["utterance"].encode('utf-8') + "\")\n")
            output.write("\t(targetFormula "  + data[j*k+i]["targetFormula"].encode('utf-8') + ")\n)")
            output.write("\n")
            json.dump(data[j*k+i], output_json)
            if i < len(data)/k-1:
                output_json.write(",\n")
        output_json.write("\n]")
