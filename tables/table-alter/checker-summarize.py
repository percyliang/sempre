#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-o', '--outfile')
    parser.add_argument('checker_filenames', nargs='+')    
    args = parser.parse_args()

    data = []

    for filename in args.checker_filenames:
        with open(filename) as fin:
            for line in fin:
                line = line.strip()
                if not line:
                    continue
                ex_id, annotation, parser = line.split('\t')
                data.append((ex_id, annotation, parser))

    stats = {
        'n':          len(data),
        'good':       sum(x[1] == 'good' for x in data),
        'incorrect':  sum(x[1] == 'incorrect' for x in data),
        'no':         sum(x[1] == 'no' for x in data),
        'good_yes':   sum(x[1] == 'good' and x[2] == 'yes' for x in data),
        'good_reach': sum(x[1] == 'good' and x[2] == 'reach' for x in data),
        'good_no':    sum(x[1] == 'good' and x[2] == 'no' for x in data),
        }
    # compute percentage
    for key in list(stats):
        stats[key + '_percent'] = stats[key] * 100. / stats['n']
    if args.outfile:
        with open(args.outfile, 'w') as fout:
            print >> fout, json.dumps(stats)
    else:
        print json.dumps(stats)

if __name__ == '__main__':
    main()

