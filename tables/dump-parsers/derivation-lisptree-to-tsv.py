#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Convert derivation LispTree to TSV.

Each input line has the following format:
  (derivation
    (formula ...)
    (value ...)
    (type ...)
    (canonicalUtterance ...)
  )

Currently prints out 'formula <tab> size'
"""

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

import gzip, fileinput
import lisptree

def process(line):
    line = line.strip()
    if line.startswith('Processing'):
        print '#' * 20, line[:-1], '#' * 20
    if not line.startswith('(derivation '):
        return
    # Just find matching parentheses
    # Wow this is an awful hack
    line = re.sub(r'^\(derivation \(formula ', '', line)
    num_open = 0
    stuff = []
    for char in line:
        stuff.append(char)
        if char == '(':
            num_open += 1
        elif char == ')':
            num_open -= 1
            if not num_open:
                break
    formula = ''.join(stuff)
    size = int(re.search(r'\$ROOT:(\d+)', line).group(1))
    print '\t'.join(str(x) for x in [formula, size])

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    args = parser.parse_args()

    if args.infile != '-':
        opener = gzip.open if args.infile.endswith('.gz') else open 
        with opener(args.infile, 'r', 'utf8') as fin:
            for line in fin:
                process(line)
    else:
        for line in sys.stdin:
            process(line)

if __name__ == '__main__':
    main()

