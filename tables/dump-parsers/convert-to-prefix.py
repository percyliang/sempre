#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Convert LispTree formulas to Prefix encoding.

Assume implicit join.
Skip formulas that cannot be converted.

If the file is tab-separated, only process the first column.
"""

import sys, os, shutil, re, argparse, json, gzip
from codecs import open
from itertools import izip
from collections import defaultdict

import fileinput
import lisptree

NUMBER_PREFIX = 'N'
DATE_PREFIX = 'D'
TYPE_ROW = 'type-row'
AGGREGATE = ['count', 'min', 'max', 'sum', 'avg']
MERGE_ARITH = ['and', 'or', '-']
SUPERLATIVE = ['argmin', 'argmax']

def convert(tree, args):
    answer = []
    def recurse(subtree):
        if isinstance(subtree, basestring):
            answer.append(subtree)
            return
        if isinstance(subtree[0], basestring):
            opr = subtree[0]
            if opr in AGGREGATE:
                assert len(subtree) == 2, str(subtree)
                answer.append(opr)
                recurse(subtree[1])
            elif opr in MERGE_ARITH:
                assert len(subtree) == 3, str(subtree)
                if opr == '-':
                    opr = 'diff'
                answer.append(opr)
                recurse(subtree[1])
                recurse(subtree[2])
            elif opr in SUPERLATIVE:
                assert len(subtree) in (3, 5), str(subtree)
                if len(subtree) == 3:
                    u, b = subtree[1], subtree[2]
                else:
                    u, b = subtree[3], subtree[4]
                assert b[0] == 'reverse'
                assert b[1][0] == 'lambda'
                answer.append(opr)
                recurse(u)
                recurse(b[1][2])
            elif opr == 'lambda':
                assert False, 'Cannot convert lambda'
                #assert len(subtree) == 3, str(subtree)
                #answer.append(opr)
                #recurse(subtree[2])
            elif opr == 'reverse':
                assert False, 'Cannot convert reverse'
                #assert len(subtree) == 2, str(subtree)
                #answer.append(opr)
                #recurse(subtree[1])
            elif opr == 'var':
                assert len(subtree) == 2, str(subtree)
                answer.append(subtree[1])
            elif opr == 'number':
                assert len(subtree) == 2, str(subtree)
                answer.append(NUMBER_PREFIX + subtree[1])
            elif opr == 'date':
                assert len(subtree) == 4, str(subtree)
                answer.append(DATE_PREFIX + '-'.join(
                    'XX' if x == '-1' else x for x in subtree[1:4]))
            else:    # Join with a name
                assert len(subtree) == 2, str(subtree)
                if ('fb:type.object.type' == opr and
                        'fb:type.row' == subtree[1]):
                    answer.append(TYPE_ROW)
                else:
                    answer.append(opr)
                    recurse(subtree[1])
        else:   # Join with a complex construct
            assert len(subtree) == 2, str(subtree)
            # Only allows ((reverse ...) ...)
            assert subtree[0][0] == 'reverse', str(subtree)
            assert len(subtree[0]) == 2, str(subtree)
            answer.append('!' + subtree[0][1])
            recurse(subtree[1])
    recurse(tree)
    return answer

def prenormalize(lf, args):
    lf = lf[:]
    # Mark all normalization relations
    for i in xrange(len(lf)):
        if lf[i].startswith('fb:cell.cell.'):
            assert lf[i-1].startswith('fb:row.row.')
            lf[i-1] = 'fb:row.{}.{}'.format(
                    lf[i][len('fb:cell.cell.'):],
                    lf[i-1][len('fb:row.row.'):])
            lf[i] = ''
        elif lf[i].startswith('!fb:cell.cell.'):
            assert lf[i+1].startswith('!fb:row.row.')
            lf[i+1] = '!fb:row.{}.{}'.format(
                    lf[i][len('!fb:cell.cell.'):],
                    lf[i+1][len('!fb:row.row.'):])
            lf[i] = ''
    return [x for x in lf if x]

def process(line, args):
    line = line.rstrip('\n').split('\t')
    lf = lisptree.parse(line[args.field])
    lf = convert(lf, args)
    lf = prenormalize(lf, args)
    line[args.field] = ' '.join(lf)
    print '\t'.join(line)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-f', '--field', type=int, default=0,
            help='Field index (tab-separated; 0-based) containing the logical form')
    parser.add_argument('infile')
    args = parser.parse_args()

    if args.infile != '-':
        opener = gzip.open if args.infile.endswith('.gz') else open 
        with opener(args.infile, 'r', 'utf8') as fin:
            for line in fin:
                process(line, args)
    else:
        for line in sys.stdin:
            try:
                process(line, args)
            except Exception as e:
                print >> sys.stderr, 'ERROR:', line.rstrip()

if __name__ == '__main__':
    main()

