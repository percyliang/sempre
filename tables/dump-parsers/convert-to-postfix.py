#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Convert LispTree formulas to Postfix.

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
    u_stack = []
    def recurse(subtree):
        if isinstance(subtree, basestring):
            answer.append(subtree)
            return
        if isinstance(subtree[0], basestring):
            opr = subtree[0]
            if opr in AGGREGATE:
                assert len(subtree) == 2, str(subtree)
                recurse(subtree[1])
                answer.append(opr)
            elif opr in MERGE_ARITH:
                assert len(subtree) == 3, str(subtree)
                recurse(subtree[1])
                recurse(subtree[2])
                answer.append(opr)
            elif opr in SUPERLATIVE:
                assert len(subtree) in (3, 5), str(subtree)
                if len(subtree) == 3:
                    u, b = subtree[1], subtree[2]
                else:
                    u, b = subtree[3], subtree[4]
                if args.implicit_superlative_lambda:
                    assert b[0] == 'reverse'
                    assert b[1][0] == 'lambda'
                    u_stack.append(convert(u, args))
                    recurse(b[1][2])
                    answer.append(opr)
                    u_stack.pop()
                else:
                    recurse(u)
                    recurse(b)
                    answer.append(opr)
            elif opr == 'lambda':
                assert len(subtree) == 3, str(subtree)
                recurse(subtree[2])
                answer.append(opr)
            elif opr == 'reverse':
                assert len(subtree) == 2, str(subtree)
                recurse(subtree[1])
                answer.append(opr)
            elif opr == 'var':
                assert len(subtree) == 2, str(subtree)
                if args.implicit_superlative_lambda:
                    answer.extend(u_stack[-1])
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
                if (args.collapse_type_row and
                        'fb:type.object.type' == opr and
                        'fb:type.row' == subtree[1]):
                    answer.append(TYPE_ROW)
                else:
                    recurse(subtree[1])
                    answer.append(opr)
                    if not args.implicit_join:
                        answer.append('join')
        else:   # Join with a complex construct
            assert len(subtree) == 2, str(subtree)
            # Only allows ((reverse ...) ...)
            assert subtree[0][0] == 'reverse', str(subtree)
            assert len(subtree[0]) == 2, str(subtree)
            recurse(subtree[1])
            answer.append('!' + subtree[0][1])
            if not args.implicit_join:
                answer.append('join')
    recurse(tree)
    return answer

def process(line, args):
    line = line.rstrip('\n').split('\t')
    line[args.field] = ' '.join(convert(lisptree.parse(line[args.field]), args))
    print '\t'.join(line)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-j', '--implicit-join', action='store_true',
            help='Do not output "." for joins')
    parser.add_argument('-s', '--implicit-superlative-lambda', action='store_true',
            help='Do not output "lambda reverse" for superlatives')
    parser.add_argument('-t', '--collapse-type-row', action='store_true',
            help='Collapse "(type row)" into a single token')
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
            process(line, args)

if __name__ == '__main__':
    main()

