#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Take in lines of derivations
and print the abbreviated formulas."""

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

import gzip

def process(line):
    line = line.strip()
    if line.startswith('Processing'):
        print '#' * 20, line[:-1], '#' * 20
    if not line.startswith('(derivation '):
        return
    # Extract only formula
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
    line = ''.join(stuff)
    # Abbreviate stuff
    line = re.sub(r'\(reverse ([^()]*)\)', r'!\1', line)
    line = re.sub(r'\(number ([^()]*)\)', r'\1', line)
    line = line.replace('fb:row.row.next', '@next')
    line = line.replace('fb:row.row.index', '@index')
    line = line.replace('fb:type.object.type', '@type')
    line = line.replace('fb:type.row', '@row')
    line = line.replace('fb:row.row.', 'r.')
    line = line.replace('fb:cell.cell.', '@p.')
    line = line.replace('@p.number', '@p.num')
    line = line.replace('fb:cell.', 'c.')
    print line

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

