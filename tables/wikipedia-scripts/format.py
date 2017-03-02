#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Reformat Wikipedia article names (e.g. change _ to space)"""

import sys, os, re, argparse, json, gzip
from codecs import open
from collections import defaultdict

def normalize(x):
    x = x.split('\t')[1].decode('utf8') \
         .replace(r"\'", "'").replace(r'\"', '"') \
         .replace('\\\\', '\\').replace('_', ' ')
    return re.sub('\([^)]*\)$', '', x).strip().encode('utf8')

def read(fin):
    cat_to_pages = {}
    line = fin.readline()
    cat_count = 0
    while line:
        cat_name = normalize(line[:-1])
        pages = []
        n = int(fin.readline())
        for i in xrange(n):
            pages.append(normalize(fin.readline()[:-1]))
        assert fin.readline() == '\n'
        cat_to_pages[cat_name] = pages
        cat_count += 1
        if cat_count % 100000 == 0:
            print >> sys.stderr, 'Found %d cats so far ...' % cat_count
        line = fin.readline()
    return cat_to_pages

def write(cat_to_pages, fout):
    for cat, pages in sorted(cat_to_pages.items(), key=lambda x: -len(x[1])):
        fout.write(cat + '\n')
        fout.write(str(len(pages)) + '\n')
        for page in pages:
            fout.write(page + '\n')
        fout.write('\n')

def main():
    parser = argparse.ArgumentParser()
    args = parser.parse_args()
    with gzip.open('grouped.txt.gz', 'rb') as fin:
        cat_to_pages = read(fin)
    with gzip.open('grouped-formatted.txt.gz', 'wb') as fout:
        write(cat_to_pages, fout)

if __name__ == '__main__':
    main()
