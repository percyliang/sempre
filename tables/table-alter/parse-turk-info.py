#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    parser.add_argument('-p', '--plot', action='store_true',
            help='Plot number of classes vs number of denotations')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('-s', '--summarize', action='store_true',
            help='Summarize the number of classes')
    group.add_argument('-d', '--dump', action='store_true',
            help='Dump the list of examples with at least one agreed classes')
    group.add_argument('-D', '--dataset-file',
            help='Take a dataset file and print a filtered list of only examples'
            ' with at least one agreed classes')
    args = parser.parse_args()

    data = []
    with open(args.infile) as fin:
        print >> sys.stderr, 'Reading from', args.infile
        header = fin.readline().rstrip('\n').split('\t')
        for line in fin:
            data.append(dict(zip(header, line.rstrip('\n').split('\t'))))
    print >> sys.stderr, 'Read', len(data), 'records.'
    # ['id', 'numDerivs', 'allTurkedTables', 'agreedTurkedTables',
    #  'origTableTarget', 'origTableTurkedTarget', 'origTableFlag',
    #  'numClassesMatched', 'numDerivsMatched']

    # Classify examples
    no_derivs = []
    orig_table_mismatch = []
    no_classes_matched = []
    classes_matched = defaultdict(list)

    plt_num_classes, plt_num_derivs = [], []

    for record in data:
        if record['numDerivs'] == '0':
            no_derivs.append(record)
            assert record['numDerivsMatched'] == '0'
            continue
        if record['origTableFlag'] == 'mismatched':
            orig_table_mismatch.append(record)
            #assert record['numDerivsMatched'] == '0'
            continue
        if record['numClassesMatched'] == '0':
            no_classes_matched.append(record)
            assert record['numDerivsMatched'] == '0'
            continue
        assert record['numDerivsMatched'] != '0'
        num_classes = int(record['numClassesMatched'])
        plt_num_classes.append(num_classes)
        plt_num_derivs.append(int(record['numDerivsMatched']))
        if num_classes < 10:
            classes_matched[num_classes].append(record)
        else:
            classes_matched['> 10'].append(record)

    if args.summarize:
        print 'No derivs:', len(no_derivs)
        print 'Original table mismatched:', len(orig_table_mismatch)
        print 'No classes matched:', len(no_classes_matched)
        print 'Classes matched:'
        total = 0
        for key in sorted(classes_matched):
            num_matches = len(classes_matched[key])
            total += num_matches
            print '  {}: {} (cum = {})'.format(key, num_matches, total)

    if args.plot:
        import matplotlib.pyplot as plt
        plt.scatter(plt_num_classes, plt_num_derivs)
        plt.show()

    if args.dump:
        for key in sorted(classes_matched):
            for x in classes_matched[key]:
                print x['id']

    if args.dataset_file:
        indices = set(int(x['id'].replace('nt-', ''))
                for y in classes_matched.values() for x in y)
        count_all, count_filtered = 0, 0
        with open(args.dataset_file, 'r', 'utf8') as fin:
            for i, line in enumerate(fin):
                count_all += 1
                if i in indices:
                    print line.rstrip('\n')
                    count_filtered += 1
        print >> sys.stderr, 'Printed {} / {} lines'.format(count_filtered, count_all)


if __name__ == '__main__':
    main()

