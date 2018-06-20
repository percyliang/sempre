#!/usr/bin/python

import sys
import json

# Official evaluation script used to evaluate Freebase question answering
# systems.  Used for EMNLP 2013, ACL 2014 papers, etc.

if len(sys.argv) != 3:
  sys.exit("Usage: %s <generated_file> <filtered_file>" % sys.argv[0])

generated = set()
with open(sys.argv[1]) as f:
  for line in f:
    generated.add(line)

out = open(sys.argv[2],'w')


with open(sys.argv[1]) as f:
  for line in f:
    index = line.find(" not")
    if index != -1:
      newStr = line[0:index] + line[index+4:len(line)]
      if newStr in generated:
        out.write(line)
    else:
      out.write(line)
out.close()
