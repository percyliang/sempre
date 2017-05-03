#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Dump the best logical form for each example."""

import sys, os, shutil, re, argparse, json, gzip
from codecs import open
from itertools import izip
from collections import defaultdict

################################################
# Data storage

CHECK_VALUE_TO_FLAG = {
    '(name Ao)': 'Ao',  # Turkers agree on an answer; LF agrees
    '(name Ax)': 'Ax',  # Turkers agree on an answer; LF disagrees
    '(name Bo)': 'Bo',  # Turkers agree that there's no answer; LF agrees
    '(name Bx)': 'Bx',  # Turkers agree that there's no answer; LF disagrees
    '(name X)': 'X',   # Turkers cannot reach a consensus 
    'null': '?',
    }

def read_check_denotations_file(filename):
    """Return n, k, d, denotations, grid"""
    with gzip.open(filename) as fin:
        n, k, d = [int(x) for x in fin.readline().split()]
        denotations = []
        for i in xrange(d):
            denotations.append(CHECK_VALUE_TO_FLAG[fin.readline().strip()])
        denotations.append(None)
        grid = []
        for i in xrange(n):
            row = [denotations[int(x)] for x in fin.readline()[:-1].split()]
            grid.append(row)
        denotations.pop()
        return n, k, d, denotations, grid

def clean(x):
    """Remove original strings from NameValues."""
    x = x.decode('utf8').replace(ur'\"', '')
    x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*)\)', r'(name \1)', x)
    x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*) [^")]*\)', r'(name \1)', x)
    x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*) "[^"]*"\)', r'(name \1)', x)
    return x


################################################
# Experiments

# Scheme is a function that takes in a check vector (on turked tables)
#   and returns either True (Turked result matches the EC)
#   or False (Turked result filters out the EC).
SCHEMES = {
    # Here are the different schemes
    # [e] LF must agree with everything the Turkers agree on
    #     (including agreeing upon not having an answer)
    'e': lambda vector: all(x != 'Ax' and x != 'Bx' for x in vector),
    # [A] When the Turkers agree on an answer, LF must agree with that
    #     Also the Turkers must agree on the original table
    'A': lambda vector: vector and vector[0][0] == 'A' and all(x != 'Ax' for x in vector),
    # [a] When the Turkers agree on an answer, LF must agree with that
    'a': lambda vector: all(x != 'Ax' for x in vector),
    # [l] Allow LF to disagree with Turkers at most once
    'l': lambda vector: sum([x == 'Ax' for x in vector], 0) <= 1,
    }

def process(ex_id, filename, scheme):
    print >> sys.stderr, 'Processing', ex_id

    # Read the "check" file: agreement with the Turked data
    n, k, d, denotations, check_grid = read_check_denotations_file(filename)
    if not n:
        return 0, []

    # Which tables did we Turk on?
    turked_tables = [i for (i,x) in enumerate(check_grid[0]) if x is not None]
    # turk_flags contains only A, B, or X
    turk_flags = ''.join(check_grid[0][i][0] for i in turked_tables)
    print >> sys.stderr, turked_tables, turk_flags

    lf_matched = []
    for i, vector in enumerate(check_grid):
        if scheme([vector[x] for x in turked_tables]):
            lf_matched.append(i)
    return n, lf_matched

def get_best_lf(ex_id, filename, indices, return_all=False):
    lfs = []
    with gzip.open(filename) as fin:
        for line in fin:
            line = line.strip()
            if not line.startswith('(derivation '):
                continue
            # Just find matching parentheses
            # Wow this is an awful hack
            line = re.sub(r'^\(derivation \(formula ', '', line)
            num_open = 0
            stuff = []
            for char in line:
                stuff.append(char)
                if char == '(':
                    num_open += 1
                elif char == ')':
                    num_open -= 1
                    if not num_open:
                        break
            formula = ''.join(stuff)
            size = int(re.search(r'\$ROOT:(\d+)', line).group(1))
            lfs.append((size, formula))
    lfs = [lfs[i] for i in indices]
    lfs.sort()
    return lfs if return_all else lfs[0]

################################################
# Main entry

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-s', '--scheme', choices=list(SCHEMES), default='a')
    # ./run @mode=tables @class=dump @parser=grow-dpd -cheat -numExamplesPerFile 1
    parser.add_argument('dump_dir')
    # ./run @mode=tables @class=alter-ex -dumpdir [above] -turkedDataPath ...
    parser.add_argument('turk_ex_dir')
    parser.add_argument('-a', '--dump-all',
            help='Dump all matching LFs. Specify the base directory here')
    args = parser.parse_args()

    if args.dump_all:
        assert os.path.isdir(args.dump_all)
    
    # Note relevant files
    check_denotations_files = {}
    path = os.path.join(args.turk_ex_dir, 'check-denotations')
    for filename in os.listdir(path):
        match = re.match(r'nt-(\d+)\.gz', filename)
        assert match, 'Invalid filename: ' + os.path.join(path, filename)
        ex_id = int(match.group(1))
        assert ex_id not in check_denotations_files, 'Repeated example id: ' + ex_id
        check_denotations_files[ex_id] = os.path.join(path, filename)
    print >> sys.stderr, 'Found', len(check_denotations_files), 'check-denotations files'
    
    dump_files = {}
    path = args.dump_dir
    for filename in os.listdir(path):
        if re.match(r'dumped-.*.gz', filename):
            with gzip.open(os.path.join(path, filename)) as fin:
                ex_id = None
                for line in fin:
                    line = line.strip()
                    if line.strip().startswith('(id '):
                        match = re.match(r'\(id nt-(\d+)\)', line)
                        assert match, 'Invalid ID: ' + line
                        ex_id = int(match.group(1))
                        break
                assert ex_id is not None, 'ID not found in ' + filename
                dump_files[ex_id] = os.path.join(path, filename)
    print >> sys.stderr, 'Found', len(check_denotations_files), 'dump files'

    for ex_id, filename in sorted(check_denotations_files.items()):
        n, matched = process(ex_id, filename, SCHEMES[args.scheme])
        if matched:
            print >> sys.stderr, ex_id, n, len(matched)
            if args.dump_all:
                lfs = get_best_lf(ex_id, dump_files[ex_id], matched, True)
                path = os.path.join(args.dump_all, 'nt-' + str(ex_id) + '.gz')
                with gzip.open(path, 'w') as fout:
                    for size, lf in lfs:
                        print >> fout, lf
            else:
                size, best_lf = get_best_lf(ex_id, dump_files[ex_id], matched)
                print '\t'.join(unicode(x) for x in ['nt-' + str(ex_id), best_lf])

if __name__ == '__main__':
    main()

