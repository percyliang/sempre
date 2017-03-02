#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def process(line):
    # Extract only formula
    line = re.sub(r'^ *True@\d+: \(derivation \(formula ', '', line)
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
    return line

def dump(formulas):
    formulas.sort()
    for x in formulas:
        print x

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    args = parser.parse_args()
    formulas = []

    with open(args.infile, 'r', 'utf8') as fin:
        for line in fin:
            if line.strip().startswith('Example: '):
                if formulas:
                    dump(formulas)
                print '#', re.sub('^Example: ', '', line.strip())
            elif line.strip().startswith('True@'):
                formulas.append(process(line))
        if formulas:
            dump(formulas)

if __name__ == '__main__':
    main()

