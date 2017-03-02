#!/usr/bin/env python
# -*- coding: utf-8 -*-
# Get the frequencies of the table headers

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

import glob

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-p', '--path', default='csv/*/*.tsv')
    args = parser.parse_args()
    
    token_counts = defaultdict(int)
    phrase_counts = defaultdict(int)

    filenames = glob.glob(args.path)
    print >> sys.stderr, 'Reading %d files' % len(filenames)
    for filename in filenames:
        with open(filename, 'r', 'utf8') as fin:
            header = fin.readline().rstrip().split('\t')
        for phrase in header:
            phrase = phrase.lower().replace(r'\n', ' ')
            phrase_counts[phrase] += 1
            for word in re.sub('[^a-z0-9]', ' ', phrase).strip().split():
                token_counts[word] += 1
    print 'Phrases'
    for x, y in sorted(phrase_counts.items(), key=lambda x: (-x[1], x[0])):
        print '%10d %s' % (y, x)
    print '#' * 60
    print 'Tokens'
    for x, y in sorted(token_counts.items(), key=lambda x: (-x[1], x[0])):
        print '%10d %s' % (y, x)

if __name__ == '__main__':
    main()

