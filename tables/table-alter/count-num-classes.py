#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Fix up the turk-info.tsv file.
- Add numClasses field
- Swap numClassesMatched and numDerivsMatched
"""

import sys, os, shutil, re, argparse, json, gzip
from codecs import open
from itertools import izip
from collections import defaultdict



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('turk_info_file',
            help='path to turk info file')
    parser.add_argument('denotation_dirs', nargs='+',
            help='directories to search for dumped denotations')
    args = parser.parse_args()
    
    # Read denotation directories
    ex_id_to_path = {}
    for directory in args.denotation_dirs:
        for filename in os.listdir(directory):
            match = re.match(r'(nt-\d+)\.gz', filename)
            assert match, filename
            ex_id_to_path[match.group(1)] = os.path.join(directory, filename)
    print >> sys.stderr, 'There are', len(ex_id_to_path), 'denotation files'

    def compute_num_classes(ex_id):
        with gzip.open(ex_id_to_path[ex_id]) as fin:
            n, k, d = [int(x) for x in fin.readline().split()]
            for i in xrange(d):
                fin.readline()
            unique_denotation_tuples = set()
            for j in xrange(n):
                unique_denotation_tuples.add(fin.readline())
            return len(unique_denotation_tuples)

    data = []
    with open(args.turk_info_file, 'r', 'utf8') as fin:
        header = fin.readline().rstrip('\n').split('\t')
        header.insert(1, 'numClasses')
        header[8], header[9] = header[9], header[8]
        print u'\t'.join(header)
        for i, line in enumerate(fin):
            if i % 500 == 0:
                print >> sys.stderr, 'Processing', i
            line = line.rstrip('\n').split('\t')
            line.insert(1, str(compute_num_classes(line[0])))
            line[8], line[9] = line[9], line[8]
            print u'\t'.join(line)
    print >> sys.stderr, 'DONE!'

if __name__ == '__main__':
    main()

