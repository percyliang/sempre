#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def count(x, verbose=False):
    # Canonicalize stuff
    x = x.strip()
    if verbose:
        print x
    x = re.sub(r'\(argmax \(number \d\) \(number \d\)', '(argmax', x)
    x = re.sub(r'\(argmin \(number \d\) \(number \d\)', '(argmin', x)
    x = re.sub(r'\(reverse ([^()]*)\)', r'!\1', x)
    x = re.sub(r'fb:row\.row\.next', r'@next', x)
    x = re.sub(r'fb:row\.row\.index', r'@index', x)
    x = re.sub(r'fb:row\.row\.[^() ]*', r'@rel', x)
    x = re.sub(r'\(fb:type\.object\.type fb:type\.row\)', r'@rows', x)
    x = re.sub(r'fb:cell_[^() ]*', r'@cell', x)
    x = re.sub(r'fb:cell\.cell\.number', r'@p.num', x)
    x = re.sub(r'fb:cell\.cell\.date', r'@p.date', x)
    x = re.sub(r'\(number [^() ]*\)', '@num', x)
    x = re.sub(r'\(date [^()]*\)', '@date', x)
    x = re.sub(r'lambda [^() ]*', 'lambda', x)
    x = re.sub(r'\(var [^()]*\)', '', x)
    x = ' '.join(re.sub(r'[()]', '', x).strip().split())
    x = re.sub(r'reverse lambda', 'lambda', x)
    if verbose:
        print '>>>', x
    return len(x.strip().split())

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    parser.add_argument('-l', '--limit', type=int, default=100000)
    parser.add_argument('-v', '--verbose', action='store_true')
    args = parser.parse_args()

    counts = defaultdict(int)
    entities = defaultdict(int)
    relations = defaultdict(int)
    with open(args.infile) as fin:
        for i, line in enumerate(fin):
            # Count entities + relations
            predicates = re.sub(r'[()!]', ' ', line).strip().split()
            if args.verbose:
                print predicates
            for x in predicates:
                if re.match(r'fb:row\.row\..*', x):
                    relations[x] += 1
                elif re.match(r'fb:cell_.*', x):
                    entities[x] += 1
            # Count number of normalized predicates
            counts[count(line.strip(), args.verbose)] += 1
            if i >= args.limit:
                break
    if args.verbose:
        print '*' * 50
        for (k, v) in sorted(entities.items(), key=lambda x: -x[1]):
            print '%6d : %s' % (v, k)
        print '*' * 50
        for (k, v) in sorted(relations.items(), key=lambda x: -x[1]):
            print '%6d : %s' % (v, k)
        print '*' * 50
    print 'Entities: %d' % len(entities)
    print 'Relations: %d' % len(relations)
    for x, y in sorted(counts.items()):
        print '%2d : %5d' % (x, y)

if __name__ == '__main__':
    main()

