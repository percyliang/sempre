#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Write a simple sequence-to-sequence dataset
in the default format of nn-semparse.

- Only the shortest formula is retained.
- Optionally, example ID is given as the first column
"""

import sys, os, shutil, re, argparse, json, gzip, random
from codecs import open
from itertools import izip
from collections import defaultdict

def get_formula(fpost):
    formulas = []
    for line in fpost:
        formula, size = line[:-1].split('\t')
        formulas.append([formula, int(size)])
    if not formulas:
        return ''
    # Choose a formula
    best_size = min(x[1] for x in formulas)
    best_formulas = [x[0] for x in formulas if x[1] == best_size]
    return random.choice(best_formulas)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('annotated_path',
            help='paths to annotated TSV')
    parser.add_argument('dataset_path',
            help='path to dataset TSV')
    parser.add_argument('postfix_dir',
            help='path to postfix directory')
    parser.add_argument('-e', '--ex-id', action='store_true',
            help='add example ID as the first column')
    parser.add_argument('-n', '--no-skip', action='store_true',
            help='do not skip examples without formula')
    args = parser.parse_args()

    # Read annotated data
    annotated = {}
    with open(args.annotated_path, 'r', 'utf8') as fin:
        header = fin.readline()[:-1].split('\t')
        for line in fin:
            record = dict(zip(header, line[:-1].split('\t')))
            annotated[record['id']] = record
    
    # Read dataset in order
    count = 0
    with open(args.dataset_path, 'r', 'utf8') as fin:
        header = fin.readline()[:-1].split('\t')
        for i, line in enumerate(fin):
            if i % 500 == 0:
                print >> sys.stderr, 'Processing Line', i, '...'
            record = dict(zip(header, line[:-1].split('\t')))
            record = annotated[record['id']]
            ex_id = record['id']
            question = record['tokens'].split('|')
            with gzip.open(os.path.join(args.postfix_dir, ex_id + '.gz')) as fpost:
                random.seed(ex_id)
                formula = get_formula(fpost)
            if formula or args.no_skip:
                fields = [' '.join(question), formula]
                if args.ex_id:
                    fields.insert(0, ex_id)
                print u'\t'.join(fields)
                count += 1
    print >> sys.stderr, 'Printed', count, 'examples.'

if __name__ == '__main__':
    main()

