#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile1')
    parser.add_argument('infile2')
    args = parser.parse_args()

    values = defaultdict(float)

    with open(args.infile1, 'r', 'utf8') as fin:
        for line in fin:
            source, target, value = line.split('\t')
            value = float(value)
            values[source, target] += value
    with open(args.infile2, 'r', 'utf8') as fin:
        for line in fin:
            target, source, value = line.split('\t')
            value = float(value)
            values[source, target] *= value
    for (source, target), value in sorted(values.items(), key=lambda x: -x[1]):
        print '\t'.join((source, target, '%.6f' % (value)))

if __name__ == '__main__':
    main()

