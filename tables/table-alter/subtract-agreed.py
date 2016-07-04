#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-z', '--remove-zeros', action='store_true',
            help='remove lines with zero score (= no candidates)')
    parser.add_argument('retained')
    parser.add_argument('aggregated')
    args = parser.parse_args()
    
    agreed = defaultdict(set)

    # Read Turk aggregated data
    with open(args.aggregated, 'r', 'utf8') as fin:
        for line in fin:
            tokens = line.rstrip().split('\t')
            ex_id = tokens[1]
            table_id = int(tokens[2])
            flag = tokens[3]
            if flag != 'X':
                agreed[ex_id].add(table_id)

    # Read Retained
    with open(args.retained, 'r', 'utf8') as fin:
        for line in fin:
            ex_id, score, alter_table_ids = line.rstrip().split('\t')
            if args.remove_zeros and float(score) == 0:
                continue
            alter_table_ids = [int(x) for x in alter_table_ids.split()]
            non_agreed = [x for x in alter_table_ids if x not in agreed[ex_id]]
            if non_agreed:
                print '\t'.join([ex_id, score, ' '.join(str(x) for x in sorted(non_agreed))])

if __name__ == '__main__':
    main()

