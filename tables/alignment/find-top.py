#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json, math
from codecs import open
from itertools import izip
from collections import defaultdict


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    parser.add_argument('-f', '--min-freq', type=float, default=5)
    parser.add_argument('-l', '--limit', type=int, default=20)
    parser.add_argument('-n', '--line-limit', type=int)
    args = parser.parse_args()

    word_pred_counts = defaultdict(float)
    word_counts = defaultdict(float)
    pred_counts = defaultdict(float)

    with open(args.infile, 'r', 'utf8') as fin:
        for i, line in enumerate(fin):
            if i % 10000 == 0:
                sys.stderr.write('Reading line %d ...\r' % i)
            if args.line_limit and i >= args.line_limit:
                break
            _id, n, words, preds = line.strip().split('\t')
            score = 1. / float(n)
            words = set(words.split())
            preds = set(preds.split())
            for word in words:
                word_counts[word] += score
                word_counts[None] += score
            for pred in preds:
                pred_counts[pred] += score
                pred_counts[None] += score
            for word in words:
                for pred in preds:
                    word_pred_counts[word, pred] += score
                    word_pred_counts[word, None] += score
                    word_pred_counts[None, pred] += score
                    word_pred_counts[None, None] += score
    print >> sys.stderr, 'DONE!', ' ' * 30
    print >> sys.stderr, '    all: #words = %d / #preds = %d' % (len(word_counts), len(pred_counts))

    # Remove rare words and rare preds
    good_words = set(x for x in word_counts if word_counts[x] >= args.min_freq or x is None)
    good_preds = set(x for x in pred_counts if pred_counts[x] >= args.min_freq or x is None)
    word_pred_counts = defaultdict(float, (x for x in word_pred_counts.items()
            if x[0][0] in good_words and x[0][1] in good_preds))
    word_counts = defaultdict(float, (x for x in word_counts.items() if x[0] in good_words))
    pred_counts = defaultdict(float, (x for x in pred_counts.items() if x[0] in good_preds))
    print >> sys.stderr, 'no rare: #words = %d / #preds = %d' % (len(word_counts), len(pred_counts))

    # For each word, print the top co-occurring preds
    for word, word_f in sorted(word_counts.items(), key=lambda x: -x[1]):
        if word is None: continue
        print '=' * 20, 'word = %s (%d)' % (word, word_f), '=' * 20
        preds = [(pred, word_pred_counts[word, pred]) for pred in pred_counts if pred is not None]
        for pred, pred_f in sorted(preds, key=lambda x: -x[1])[:args.limit]:
            print 'C %7.2f  %s %s' % (pred_f, word, pred)

    # For each pred, print the top co-occurring words
    for pred, pred_f in sorted(pred_counts.items(), key=lambda x: -x[1]):
        if pred is None: continue
        print '#' * 20, 'pred = %s (%d)' % (pred, pred_f), '=' * 20
        words = [(word, word_pred_counts[word, pred]) for word in word_counts if word is not None]
        for word, word_f in sorted(words, key=lambda x: -x[1])[:args.limit]:
            print 'C %7.2f  %s %s' % (word_f, word, pred)

    normalized_pmis = {}
    for word in word_counts:
        if word is None: continue
        for pred in pred_counts:
            if pred is None or word_pred_counts[word, pred] == 0: continue
            normalized_pmis[word, pred] = ((
                    math.log(word_pred_counts[word, pred])
                    + math.log(word_pred_counts[None, None])
                    - math.log(word_pred_counts[word, None])
                    - math.log(word_pred_counts[None, pred])) / (
                    math.log(word_pred_counts[None, None])
                    - math.log(word_pred_counts[word, pred])))

    print '%' * 20, 'Normalized PMI', '%' * 20
    for (word, pred), normalized_pmis in sorted(normalized_pmis.items(), key=lambda x: (x[0][1], -x[1])):
        print 'P %10.5f %s %s' % (normalized_pmis, word, pred)
        
if __name__ == '__main__':
    main()

