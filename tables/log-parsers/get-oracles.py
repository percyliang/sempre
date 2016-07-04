#!/usr/bin/env python
# -*- coding: utf-8 -*-
# Get oracles from log file

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    parser.add_argument('-d', '--dataset', default='d/unnormalized-training.examples')
    parser.add_argument('-g', '--group', default='0.train')
    parser.add_argument('-c', '--criteria', default='oracle', choices=['correct', 'oracle'])
    args = parser.parse_args()

    example_id = None
    oracles = []
    examples = []

    print >> sys.stderr, 'Group = %s' % args.group
    print >> sys.stderr, 'Criteria = %s' % args.criteria

    with open(args.infile, 'r', 'utf8') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('iter=%s: example' % args.group):
                assert example_id is None
                example_id = line.split()[3]
                examples.append(example_id)
            elif line.startswith('Current:') and example_id is not None:
                if args.criteria == 'oracle':
                    if line.split()[2].startswith('oracle=1'):
                        oracles.append(example_id)
                elif args.criteria == 'correct':
                    if line.split()[1].startswith('correct=1'):
                        oracles.append(example_id)
                example_id = None
    if not examples:
        print >> sys.stderr, 'ERROR: No examples found!'
        exit(1)

    oracles = set(oracles)
    with open(args.dataset, 'r', 'utf8') as fin:
        # Assume one example per line
        for i, line in enumerate(fin):
            match = re.search(r'\(id ([^) ]*)\)', line)
            if match and match.group(1) in oracles:
                print line.rstrip()
    print >> sys.stderr, 'Found %d / %d examples (%f)' % \
            (len(oracles), len(examples), len(oracles) * 1. / len(examples))

if __name__ == '__main__':
    main()

