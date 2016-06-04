#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Count the size of each example in the file.
"""

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

import glob, gzip

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-m', '--max-size', default=8)
    parser.add_argument('indirs', nargs='+')
    args = parser.parse_args()

    for i, indir in enumerate(sorted(args.indirs)):
        print >> sys.stderr, 'INDIR {:2}: {}'.format(i, indir)

    filenames = []
    for indir in args.indirs:
        filenames.extend(glob.glob(os.path.join(indir, '*.gz')))
    print >> sys.stderr, len(filenames), 'files.'
    
    count = 0
    for filename in sorted(filenames):
        print >> sys.stderr, 'Processing', filename
        with gzip.open(filename) as fin:
            for line in fin:
                if line.startswith('#'):
                    tokens = line.strip().split()
                    assert tokens[1] == 'Example'
                    ex_id = tokens[2]
                    sizes = defaultdict(int)
                    count += 1
                elif line.startswith(')'):
                    print '\t'.join([ex_id] + [str(sizes[i]) for i in xrange(args.max_size + 1)])
                else:
                    match = re.search(r'\$ROOT:(\d+)', line)
                    if match:
                        sizes[int(match.group(1))] += 1
            print >> sys.stderr, 'Processed', count, 'examples'

if __name__ == '__main__':
    main()

