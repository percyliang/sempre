#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

from glob import glob
from random import shuffle, sample

class Counter(object):
    def __init__(self):
        self.files = 0
        self.examples = defaultdict(list)
        self.counts = defaultdict(int)

    def process(self, fin):
        self.files += 1
        fin.readline()   # Skip header
        cells = defaultdict(set)
        for line in fin:
            for cell in line.strip().split('\t'):
                cells[self.normalize(cell)].add(cell)
        for key, value in cells.iteritems():
            self.counts[key] += 1
            self.examples[key].append(sample(value, 1)[0])

    def normalize(self, x):
        x = re.sub(r'\\n', '\n', x)
        x = re.sub(r'[A-Z]+', 'A', x)
        x = re.sub(r'[a-z]+', 'a', x)
        x = re.sub(r'[0-9]+', '0', x)
        x = re.sub('\n', r'\\n', x)
        return x

    def summarize(self):
        print 'Read', self.files, 'files.'
        for key, value in sorted(self.counts.items(), key=lambda x: (-x[1], x[0])):
            if value == 1:
                break
            print '#', value, '|', key
            shuffle(self.examples[key])
            for y in self.examples[key][:10]:
                print '>>>', y

def main():
    parser = argparse.ArgumentParser()

    args = parser.parse_args()

    counter = Counter()
    for filename in glob('csv/*-csv/*.tsv'):
        print >> sys.stderr, 'Reading', filename, '...'
        with open(filename, 'r', 'utf8') as fin:
            counter.process(fin)
    counter.summarize()

if __name__ == '__main__':
    main()

