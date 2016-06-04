#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Compute the sizes of equivalence classes.
"""

import sys, os, shutil, re, argparse, json, gzip
from codecs import open
from itertools import izip
from collections import defaultdict, Counter

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('denotation_dirs', nargs='+',
            help='directories to search for dumped denotations')
    args = parser.parse_args()
    
    # Read denotation directories
    ex_id_to_path = {}
    for directory in args.denotation_dirs:
        for filename in os.listdir(directory):
            match = re.match(r'nt-(\d+)\.gz', filename)
            assert match, filename
            ex_id_to_path[int(match.group(1))] = os.path.join(directory, filename)
    print >> sys.stderr, 'There are', len(ex_id_to_path), 'denotation files'

    def compute_num_classes(ex_id):
        with gzip.open(ex_id_to_path[ex_id]) as fin:
            n, k, d = [int(x) for x in fin.readline().split()]
            for i in xrange(d):
                fin.readline()
            counter = Counter()
            for j in xrange(n):
                counter[fin.readline()] += 1
            return counter

    for i, ex_id in enumerate(sorted(ex_id_to_path)):
        if i % 500 == 0:
            print >> sys.stderr, 'Processing', i
        sizes = compute_num_classes(ex_id).values()
        if sizes:
            counter = Counter(sizes)
            print ' '.join(('{}x{}'.format(x, y) if y > 1 else str(x))
                    for (x, y) in reversed(sorted(counter.items())))
        else:
            print 0
    print >> sys.stderr, 'DONE!'

if __name__ == '__main__':
    main()

