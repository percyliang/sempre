#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Experiments regarding fictitious tables."""

import sys, os, shutil, re, argparse, json, gzip
from codecs import open
from itertools import izip
from collections import defaultdict

def mean(x):
    x = list(x)
    return sum(x) * 1. / len(x)

def sd(x):
    x = list(x)
    return (sum(u**2 for u in x) * 1. / len(x) - mean(x)**2)**.5

################################################
# Data storage

class Data(object):
    """Store the data for all examples."""

    def __init__(self):
        self.actual_denotations_files = {}
        self.actual_annodeno_files = {}
        self.chosen_tables_data = {}

    def read_denotation_file(self, ex_id):
        """Return n, k, d, denotations, grid"""
        with gzip.open(self.actual_denotations_files[ex_id]) as fin:
            n, k, d = [int(x) for x in fin.readline().split()]
            denotations = []
            for i in xrange(d):
                denotations.append(self.clean(fin.readline().strip()))
            if not denotations:
                print >> sys.stderr, 'Warning: no denotations - ', ex_id
            elif denotations[0] == '(list)':
                print >> sys.stderr, 'Warning: denotations[0] == (list) - ', ex_id
            denotations.append(None)
            grid = []
            for i in xrange(n):
                row = [denotations[int(x)] for x in fin.readline()[:-1].split()]
                grid.append(row)
            denotations.pop()
            return n, k, d, denotations, grid

    def clean(self, x):
        """Remove original strings from NameValues."""
        x = x.decode('utf8').replace(ur'\"', '')
        x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*)\)', r'(name \1)', x)
        x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*) [^")]*\)', r'(name \1)', x)
        x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*) "[^"]*"\)', r'(name \1)', x)
        return x

    def read_annodeno_file(self, ex_id):
        with gzip.open(self.actual_annodeno_files[ex_id]) as fin:
            annodenos = []
            for line in fin:
                annodenos.append(self.clean(line.strip()))
            return annodenos

class Stats(object):
    """Store the statistics to be printed out."""
    FIELDS = [
            'exId', 'numLFs', 'numWorlds', 'numDenos', 'numECs',
            'annotated', 'numCorrectLFs',
            'eChosenTables', 'eScore',
            'eNumECsMatched', 'eNumLFsMatched',
            'ePercentECsRuledOut', 'ePercentLFsRuledOut',
            'rChosenTables',
            'rNumECsMatched', 'rNumLFsMatched',
            'rPercentECsRuledOut', 'rPercentLFsRuledOut',
            ]

    def __init__(self):
        self.stats = {}

    def __setitem__(self, key, value):
        assert key in self.FIELDS, key
        self.stats[key] = value

    def __getitem__(self, key):
        return self.stats[key]

    @classmethod
    def print_header(cls):
        print '\t'.join(cls.FIELDS)

    def print_record(self):
        print '\t'.join(str(self.stats.get(x, '')) for x in self.FIELDS)
        sys.stdout.flush()

################################################
# Experiments

NUM_GENERATED_TABLES = 31    # Including the original table

def process(data, ex_id):
    print >> sys.stderr, 'Processing', ex_id
    stats = Stats()

    # Read denotation tuples
    n, k, d, denotations, grid = data.read_denotation_file(ex_id)
    grid = [tuple(row[:NUM_GENERATED_TABLES]) for row in grid]
    stats['exId'] = ex_id
    stats['numLFs'] = n
    stats['numWorlds'] = k
    stats['numDenos'] = d

    # Map equivalence classes to list of indices
    equiv_classes = {}
    for i, row in enumerate(grid):
        equiv_classes.setdefault(row, []).append(i)
    stats['numECs'] = len(equiv_classes)

    # Read the chosen tables (e = entropy, r = random)
    stats['eScore'] = data.chosen_tables_data[ex_id][0]
    e_chosen = data.chosen_tables_data[ex_id][1]
    r_chosen = range(len(e_chosen))
    stats['eChosenTables'] = ' '.join(str(x) for x in e_chosen)
    stats['rChosenTables'] = ' '.join(str(x) for x in r_chosen)

    # Read denotations of the manually annotated logical form
    annodenos = tuple(data.read_annodeno_file(ex_id)[:NUM_GENERATED_TABLES])
    if annodenos[0] == 'null':
        stats['annotated'] = False
    else:
        stats['annotated'] = True
        # Correct logical forms based on the annotated logical form
        stats['numCorrectLFs'] = len(equiv_classes.get(annodenos, []))
        # Inferred LFs based on the denotations on the chosen tables
        def analyze_chosen(chosen, prefix):
            annodenos_on_chosen = tuple(annodenos[x] for x in chosen)
            num_ec_matched = 0
            num_lf_matched = []
            lf_matched = []
            for deno_tuple, lf_indices in equiv_classes.items():
                deno_tuple_on_chosen = tuple(deno_tuple[x] for x in chosen)
                if deno_tuple_on_chosen == annodenos_on_chosen:
                    num_ec_matched += 1
                    num_lf_matched.append(len(lf_indices))
                    lf_matched.extend(lf_indices)
            stats[prefix + 'NumECsMatched'] = num_ec_matched
            stats[prefix + 'NumLFsMatched'] = sum(num_lf_matched)
            if len(equiv_classes) > 1:
                stats[prefix + 'PercentECsRuledOut'] = (
                        (len(equiv_classes) - num_ec_matched) * 100. /
                        (len(equiv_classes) - 1))
                stats[prefix + 'PercentLFsRuledOut'] = (
                        (n - sum(num_lf_matched)) * 100. /
                        (n - stats['numCorrectLFs']))
        analyze_chosen(e_chosen, 'e')
        analyze_chosen(r_chosen, 'r')
        
    # Print the output
    stats.print_record()
    return stats

################################################
# Main entry

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-e', '--exec-dirs', nargs='+',
            help='path to output directories when running SEMPRE with @class=alter-ex')
    parser.add_argument('-R', '--range', nargs=2, type=int,
            help='range of example ids to process')
    parser.add_argument('-c', '--chosen-tables', nargs='+',
            help='TSV files specifying the indices of the chosen fictitious worlds')
    args = parser.parse_args()
    
    # Note relevant files
    data = Data()

    for dirname, target in (
            ('actual-denotations', data.actual_denotations_files),
            ('actual-annotated-denotations', data.actual_annodeno_files),
            ):
        for exec_dir in args.exec_dirs:
            path = os.path.join(exec_dir, dirname)
            if os.path.isdir(path):
                for filename in os.listdir(path):
                    match = re.match(r'nt-(\d+)\.gz', filename)
                    assert match, 'Invalid filename: ' + os.path.join(path, filename)
                    ex_id = int(match.group(1))
                    assert ex_id not in target, 'Repeated example id: ' + ex_id
                    target[ex_id] = os.path.join(path, filename)
        print >> sys.stderr, 'Found', len(target), 'files for', dirname

    for filename in args.chosen_tables:
        with open(filename) as fin:
            for line in fin:
                line = line[:-1].split('\t')
                ex_id = int(line[0][3:])
                score = float(line[1])
                chosen = [int(x) for x in line[2].split()]
                data.chosen_tables_data[ex_id] = (score, chosen)

    ex_ids = sorted(data.actual_denotations_files)
    if args.range:
        ex_ids = [x for x in ex_ids if args.range[0] <= x < args.range[1]]
    Stats.print_header()
    stats = []
    for ex_id in ex_ids:
        stats.append(process(data, ex_id))
    # Summarize
    print 'Number of examples:\t%d' % len(stats)
    print 'Avg numLFs:\t%.2f' % mean(s['numLFs'] for s in stats)
    print 'SD numLFs:\t%.2f' % sd(s['numLFs'] for s in stats)
    print 'Avg numECs:\t%.2f' % mean(s['numECs'] for s in stats)
    print 'SD numECs:\t%.2f' % sd(s['numECs'] for s in stats)
    print 'Annotated:\t%d' % sum(s['annotated']for s in stats)
    print '*** Among the annotated examples ***'
    annotated_stats = [s for s in stats if s['annotated']]
    print 'Avg numCorrectLFs:\t%.2f' % mean(s['numCorrectLFs']
            for s in annotated_stats)
    print 'SD numCorrectLFs:\t%.2f' % sd(s['numCorrectLFs']
            for s in annotated_stats)
    print 'SCHEME: Use the objective function to choose worlds'
    print 'Avg %% spurious LFs ruled out:\t%.2f' % mean(s['ePercentECsRuledOut']
            for s in annotated_stats if s['numECs'] > 1)
    print 'Avg %% spurious ECs ruled out:\t%.2f' % mean(s['ePercentLFsRuledOut']
            for s in annotated_stats if s['numECs'] > 1)
    print 'Num ECs Matched <= 1:\t%.2f' % (mean(s['eNumECsMatched'] <= 1
            for s in annotated_stats) * 100.)
    print 'Num ECs Matched <= 3:\t%.2f' % (mean(s['eNumECsMatched'] <= 3
            for s in annotated_stats) * 100.)
    print 'SCHEME: Pick random worlds to test denotations on'
    print 'Num ECs Matched <= 1:\t%.2f' % (mean(s['rNumECsMatched'] <= 1
            for s in annotated_stats) * 100.)
    print 'Num ECs Matched <= 3:\t%.2f' % (mean(s['rNumECsMatched'] <= 3
            for s in annotated_stats) * 100.)

if __name__ == '__main__':
    main()

