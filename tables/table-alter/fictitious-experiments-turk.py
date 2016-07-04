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

CHECK_VALUE_TO_FLAG = {
    '(name Ao)': 'Ao',  # Turkers agree on an answer; LF agrees
    '(name Ax)': 'Ax',  # Turkers agree on an answer; LF disagrees
    '(name Bo)': 'Bo',  # Turkers agree that there's no answer; LF agrees
    '(name Bx)': 'Bx',  # Turkers agree that there's no answer; LF disagrees
    '(name X)': 'X',   # Turkers cannot reach a consensus 
    'null': '?',
    }

class Data(object):
    """Store the data for all examples."""

    def __init__(self):
        self.actual_denotations_files = {}
        self.actual_annodeno_files = {}
        self.check_denotations_files = {}
        self.check_annodeno_files = {}
        self.chosen_tables_data = {}

    def read_denotation_file(self, ex_id, check_file=False):
        """Return n, k, d, denotations, grid"""
        if not check_file:
            filename = self.actual_denotations_files[ex_id]
        else:
            filename = self.check_denotations_files[ex_id]
        with gzip.open(filename) as fin:
            n, k, d = [int(x) for x in fin.readline().split()]
            denotations = []
            for i in xrange(d):
                denotations.append(self.clean(fin.readline().strip()))
            if not denotations:
                print >> sys.stderr, 'Warning: no denotations - ', ex_id
            elif denotations[0] == '(list)':
                print >> sys.stderr, 'Warning: denotations[0] == (list) - ', ex_id
            if check_file:
                denotations = [CHECK_VALUE_TO_FLAG[x] for x in denotations]
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

    def read_annodeno_file(self, ex_id, check_file=False):
        if not check_file:
            filename = self.actual_annodeno_files[ex_id]
        else:
            filename = self.check_annodeno_files[ex_id]
        with gzip.open(filename) as fin:
            annodenos = []
            for line in fin:
                annodenos.append(self.clean(line.strip()))
            if check_file:
                annodenos = [CHECK_VALUE_TO_FLAG[x] for x in annodenos]
            return annodenos


class Stats(object):
    """Store the statistics to be printed out."""
    FIELDS = [
            'exId', 'numLFs', 'numWorlds', 'numDenos', 'numECs',
            'annotated',
            'turkedTables', 'turkFlags', 'turkAgreesOnOrigTable',
            'eNumLFsMatched', 'eNumECsMatched', 'eAnnoMatchesTurk',
            'eCorrectAmongMatched',
            'ePercentECsRuledOut', 'ePercentLFsRuledOut',
            'aNumLFsMatched', 'aNumECsMatched', 'aAnnoMatchesTurk',
            'aCorrectAmongMatched',
            'aPercentECsRuledOut', 'aPercentLFsRuledOut',
            'lNumLFsMatched', 'lNumECsMatched', 'lAnnoMatchesTurk',
            'lCorrectAmongMatched',
            'lPercentECsRuledOut', 'lPercentLFsRuledOut',
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

    # Read the "check" file: agreement with the Turked data
    _, _, _, _, check_grid = data.read_denotation_file(ex_id, check_file=True)
    # Read denotations of the manually annotated logical form
    annodenos = tuple(data.read_annodeno_file(ex_id)[:NUM_GENERATED_TABLES])
    check_annodenos = data.read_annodeno_file(ex_id, check_file=True)
    stats['annotated'] = (annodenos[0] != 'null')

    # Which tables did we Turk on?
    if check_grid:
        turked_tables = [i for (i,x) in enumerate(check_grid[0])
                if x is not None]
        # turk_flags contains only A, B, or X
        turk_flags = ''.join(check_grid[0][i][0] for i in turked_tables)
    else:
        # No logical forms produced
        turked_tables = [i for (i,x) in enumerate(annodenos) if x is not None]
        turk_flags = ''.join(check_annodenos[i][0] for i in turked_tables)
        
    stats['turkedTables'] = ' '.join(str(x) for x in turked_tables)
    stats['turkFlags'] = turk_flags
    stats['turkAgreesOnOrigTable'] = (check_annodenos[0][0] == 'A')

    # Scheme is a function that takes in a check vector (on turked tables)
    #   and returns either True (Turked result matches the EC)
    #   or False (Turked result filters out the EC).
    #
    # For the given scheme, compute:
    # - number of ECs matched
    # - number of LFs matched
    # - whether the annotated formula matches turked data
    # - whether the "correct" class is among the matched classes
    def analyze_scheme(prefix, scheme):
        lf_matched = []
        for vector in check_grid:
            lf_matched.append(scheme([vector[x] for x in turked_tables]))
        anno_matched = scheme([check_annodenos[x] for x in turked_tables])
        matched_lfs = [i for (i,x) in enumerate(lf_matched) if x]
        matched_ecs = set(grid[i] for i in matched_lfs)
        stats[prefix + 'NumLFsMatched'] = len(matched_lfs)
        stats[prefix + 'NumECsMatched'] = len(matched_ecs)
        stats[prefix + 'AnnoMatchesTurk'] = anno_matched
        stats[prefix + 'CorrectAmongMatched'] = annodenos in matched_ecs
        if annodenos in matched_ecs and len(equiv_classes) > 1:
            num_correct_lfs = len(equiv_classes[annodenos])
            stats[prefix + 'PercentECsRuledOut'] = (
                    (len(equiv_classes) - len(matched_ecs)) * 100. /
                    (len(equiv_classes) - 1))
            stats[prefix + 'PercentLFsRuledOut'] = (
                    (n - len(matched_lfs)) * 100. /
                    (n - num_correct_lfs))

    # Here are the different schemes
    # [e] LF must agree with everything the Turkers agree on
    #     (including agreeing upon not having an answer)
    analyze_scheme('e',
            lambda vector: all(x != 'Ax' and x != 'Bx' for x in vector))
    # [a] When the Turkers agree on an answer, LF must agree with that
    analyze_scheme('a',
            lambda vector: all(x != 'Ax' for x in vector))
    # [l] Allow LF to disagree with Turkers at most once
    analyze_scheme('l',
            lambda vector: sum([x == 'Ax' for x in vector], 0) <= 1)

    # Print the output
    stats.print_record()
    return stats

################################################
# Main entry

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-e', '--exec-dirs', nargs='+',
            help='path to output directories when running SEMPRE with @class=alter-ex but without the turked data')
    parser.add_argument('-t', '--exec-turk-dirs', nargs='+',
            help='path to output directories when running SEMPRE with @class=alter-ex and with the turked data')
    parser.add_argument('-R', '--range', nargs=2, type=int,
            help='range of example ids to process')
    args = parser.parse_args()
    
    # Note relevant files
    data = Data()

    def get_filenames(dirname, target, exec_dirs):
        for exec_dir in exec_dirs:
            path = os.path.join(exec_dir, dirname)
            if os.path.isdir(path):
                for filename in os.listdir(path):
                    match = re.match(r'nt-(\d+)\.gz', filename)
                    assert match, 'Invalid filename: ' + os.path.join(path, filename)
                    ex_id = int(match.group(1))
                    assert ex_id not in target, 'Repeated example id: ' + ex_id
                    target[ex_id] = os.path.join(path, filename)
        print >> sys.stderr, 'Found', len(target), 'files for', dirname

    get_filenames('actual-denotations',
            data.actual_denotations_files, args.exec_dirs)
    get_filenames('actual-annotated-denotations',
            data.actual_annodeno_files, args.exec_dirs)
    get_filenames('check-denotations',
            data.check_denotations_files, args.exec_turk_dirs)
    get_filenames('check-annotated-denotations',
            data.check_annodeno_files, args.exec_turk_dirs)

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
    print 'Agreed on original world:\t%d' % sum(s['turkAgreesOnOrigTable']
            for s in annotated_stats)
    print '*** Among examples where Turkers agree on the answer on the original world ***'
    okorig_stats = [s for s in annotated_stats if s['turkAgreesOnOrigTable']]
    for prefix, name in (
            ('e', '[e] LF must agree with everything the Turkers agree on ' +
                    '(including agreeing upon not having an answer)'),
            ('a', '[a] When the Turkers agree on an answer, LF must agree with that'),
            ('l', '[l] Allow LF to disagree with Turkers at most once'),
            ):
        print 'SCHEME:', name
        print '[%s] Annotated LF agrees with Turked data:\t%.2f' % (prefix,
                mean(s[prefix + 'AnnoMatchesTurk']
                    for s in okorig_stats) * 100.)
        print '[%s] Entire Z got pruned:\t%.2f' % (prefix,
                mean(s[prefix + 'NumECsMatched'] == 0
                    for s in okorig_stats) * 100.)
        print ('[%s] Z is not entirely pruned ' +
            'but the correct EC got pruned:\t%.2f') % (prefix,
                mean((not s[prefix + 'CorrectAmongMatched'] and
                    s[prefix + 'NumECsMatched'] != 0)
                    for s in okorig_stats) * 100.)
        print '[%s] Among the rest, avg %% spurious ECs ruled out:\t%.2f' % (prefix,
                mean(s[prefix + 'PercentECsRuledOut']
                    for s in okorig_stats
                    if s[prefix + 'CorrectAmongMatched'] and s['numECs'] > 1))
        print '[%s] Among the rest, avg %% spurious LFs ruled out:\t%.2f' % (prefix,
                mean(s[prefix + 'PercentLFsRuledOut']
                    for s in okorig_stats
                    if s[prefix + 'CorrectAmongMatched'] and s['numECs'] > 1))

if __name__ == '__main__':
    main()

