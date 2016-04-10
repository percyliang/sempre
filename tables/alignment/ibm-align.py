#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    parser.add_argument('-n', '--line-limit', type=int)
    parser.add_argument('-m', '--max-iters', type=int, default=10)
    parser.add_argument('-s', '--switch', action='store_true',
            help='switch source and target')
    parser.add_argument('-z', '--null',
            choices=['fixed', 'varied', 'trained', 'none'],
            default='trained',
            help='probability of aligning to index zero (null word)'
                + ' [fixed = fixed probability |'
                + ' varied = 1/(source length + 1) |'
                + ' trained = treat null as a normal source word |'
                + ' none = do not align to null]')
    parser.add_argument('-Z', '--null-prob',
            type=float, default=0.1,
            help='fixed probability of aligning to index zero')
    args = parser.parse_args()
   
    # (weight, words, predicates)
    data = []
    all_sources = set()
    all_targets = set()
    if not args.switch:
        print >> sys.stderr, 'SOURCE = words | TARGET = predicates'
    else:
        print >> sys.stderr, 'SOURCE = predicates | TARGET = words'
    if args.null == 'trained':
        all_sources.add(None)

    # Read data
    with open(args.infile, 'r', 'utf8') as fin:
        for i, line in enumerate(fin):
            if i % 10000 == 0:
                sys.stderr.write('Reading line %d ...\r' % i)
            if args.line_limit and i >= args.line_limit:
                break
            _id, n, words, predicates = line.strip().split('\t')
            score = 1. / float(n)
            if not args.switch:
                source = words.split()
                target = predicates.split()
            else:
                source = predicates.split()
                target = words.split()
            data.append((score, source, target))
            all_sources.update(source)
            all_targets.update(target)
    print >> sys.stderr, 'DONE!', ' ' * 30
    print >> sys.stderr, '# source tokens = %d' % len(all_sources)
    print >> sys.stderr, '# target tokens = %d' % len(all_targets)

    # Construct an alignment table
    # Initialize as uniform
    # alignment[source, target] = P(target|source)
    alignment = {}
    for source in all_sources:
        if source is None and args.null != 'trained':
            continue
        prob = 1. / len(all_targets)
        for target in all_targets:
            alignment[source, target] = prob

    # EM
    for itr in xrange(args.max_iters):
        print >> sys.stderr, 'Iteration', itr, '...'
        counts = defaultdict(float)
        for score, sources, targets in data:
            for target in targets:
                # b[j] = prob that target aligns to word j
                if args.null == 'trained':
                    sources = [None] + sources
                b = [alignment.get((x, target), 0) for x in sources]
                normalizer = sum(b) + 1e-10
                if args.null == 'fixed':
                    normalizer += args.null_prob
                elif args.null == 'varied':
                    normalizer += 1. / len(sources)
                b = [x / normalizer for x in b]
                for (source, soft_count) in zip(sources, b):
                    soft_count *= score
                    counts[source, target] += soft_count
                    counts[source, True] += soft_count
        alignment = {}
        for source in all_sources:
            for target in all_targets:
                if (source, target) in counts:
                    alignment[source, target] = \
                            counts[source, target] / counts[source, True]

    # Dump the alignment
    for (source, target), value in sorted(alignment.items(), key=lambda x: (x[0][0], -x[1])):
        print '\t'.join((unicode(source), target, '%.6f' % value))

if __name__ == '__main__':
    main()

