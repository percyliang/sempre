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
    add = "Q/"
    add2 = "Q/Qtrain-"
elif str(type) == "a":
    add = "A/"
    add2 = "A/Atrain-"
else:
    add = "./"
    add2 = "train-"


for a in range(1,k+1):
    train = open(add+str(a)+"/train.examples", "w")
    train_json = open(add+str(a)+"/train.json", "w")
    test = open(add+str(a)+"/test.examples", "w")
    test_json = open(add+str(a)+"/test.json", "w")
    train_json.write("[\n")
    test_json.write("[\n")
    for j in range(k):
        if j == a-1:
            continue
        print add2 + str(j) + ".json"
        f = codecs.open(add2 + str(j) + ".json", "r", "UTF-8")
        data = json.load(f)
        for i in range(len(data)):
            train.write("(example\n")
            train.write("\t(utterance \"" + data[i]["utterance"].encode('utf-8') + "\")\n")
            train.write("\t(targetFormula "  + data[i]["targetFormula"].encode('utf-8') + ")\n)")
            train.write("\n")
            if (j < len(data)-1) and (not i+j == 0 and not (i==0 and j == 1 and a-1 == 0)):
                train_json.write(",\n")
            train_json.write("\t")
            json.dump(data[i], train_json)
    train_json.write("\n]")
    f = codecs.open("train-" + str(a-1) + ".json", "r", "UTF-8")
    data = json.load(f)
    for i in range(len(data)):
        test.write("(example\n")
        test.write("\t(utterance \"" + data[i]["utterance"].encode('utf-8') + "\")\n")
        test.write("\t(targetFormula "  + data[i]["targetFormula"].encode('utf-8') + ")\n)")
        test.write("\n")
        if i > 0:
            test_json.write(",\n")
        test_json.write("\t")
        json.dump(data[i], test_json)
    test_json.write("\n]")
