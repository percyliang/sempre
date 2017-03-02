#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Convert dump gzip file to training and test files.
- Training file does not contain the contexts
- Test file has only the first 200 examples.

The parser is super specific to the dump produced by
./run @mode=tables @class=dump
"""

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

import gzip

def process(fin, fout_train, fout_test):
    # First line
    meta = fin.readline()
    fout_train.write(meta)
    fout_test.write("(metadata (group test) (size 200))\n")
    # The rest
    count = 0
    for line in fin:
        if line[0] == '#':
            count += 1
            if count % 500 == 0:
                print >> sys.stderr, 'Processing', count, '...' 
        if not line.startswith('  (context'):
            fout_train.write(line)
        if count <= 200:
            fout_test.write(line)
    print >> sys.stderr, 'Done processing', count, 'examples.'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    args = parser.parse_args()

    infile = args.infile
    assert infile.endswith('.gz')
    out_train = re.sub(r'\.gz$', '-train.gz', infile)
    out_test = re.sub(r'\.gz$', '-test.gz', infile)

    with gzip.open(args.infile) as fin:
        with gzip.open(out_train, 'w') as fout_train:
            with gzip.open(out_test, 'w') as fout_test:
                process(fin, fout_train, fout_test)

if __name__ == '__main__':
    main()

