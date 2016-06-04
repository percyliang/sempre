#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Dump the specified field of an SQL dump."""

import sys, os, re, argparse, json, gzip
from codecs import open
from collections import defaultdict

def parse_records(line):
    record = None
    field = None
    string = None
    quote = None
    for c in line:
        if record is None:
            if c == '(':
                record = []
                field = []
        elif string is None:
            if c == ',':
                record.append(''.join(field))
                field = []
            elif c == "'":
                string = []
            elif c == ')':
                record.append(''.join(field))
                yield record
                record = None
                field = None
            else:
                field.append(c)
        elif quote is None:
            if c == "'":
                field.append(''.join(string))
                string = None
            elif c == '\\':
                quote = '\\'
            else:
                field.append(c)
        else:
            field.append(quote + c)
            quote = None

def process(fin, fout, args):
    count = 0
    for i, line in enumerate(fin):
        print 'Processing line %6d (%10d things found so far)  ...' % (i, count)
        if not line.startswith('INSERT INTO'):
            continue
        for record in parse_records(line):
            count += 1
            record = [record[i] for i in args.indices]
            fout.write('\t'.join(record))
            fout.write('\n')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('infile', help='gzipped SQL file')
    parser.add_argument('outfile')
    parser.add_argument('-i', '--indices', nargs='+', type=int,
                        help='field indices to extract')
    parser.add_argument('-g', '--gzip', action='store_true',
                        help='write to gzip file')
    args = parser.parse_args()
    
    if not args.indices:
        print 'Error: indices cannot be empty'
        exit(1)

    with gzip.open(args.infile, 'rb') as fin:
        if args.gzip:
            with gzip.open(args.outfile + '.gz', 'wb') as fout:
                process(fin, fout, args)
        else:
            with open(args.outfile, 'wb') as fout:
                process(fin, fout, args)

if __name__ == '__main__':
    main()
