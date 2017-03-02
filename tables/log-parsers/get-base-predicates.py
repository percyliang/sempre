#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Dump all possible context predicates of each example.

Output:
    - Example ID
    - Tokens <tab> Context <tab> Canonicalized Target
    - Lines of records with the following fields (tab-separated):
        - flag (see below)
        - predicate
        - words in the original string of the predicate
      If the predicate is from an utterance, also add these fields:
        - inclusive begin index of the utterance span
        - exclusive end index of the utterance span
        - source utterance span

Flags:
    1st bit = Whether it is from the utterance
    2nd bit = Whether it is from the table
    3rd bit = Whether it is a unary (0), binary (1), or operation (2; not used)
    The next few bits indicate the type
    - fb:cell. (or a union thereof)
    - fb:part. (or a union thereof)
    - fb:row.row.
    - !fb:row.row.
    - number or number range
    - date

Interpretation of the first two flag bits:
    00 = predefined predicates (not used here)
    01 = floating stuff (relations, closed class cells)
    10 = matched values (pure numbers, pure dates, date ranges)
    11 = fuzzy matched (cells, parts, unions of cells or parts)
"""

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict, Counter

MONTH = [None, 'january', 'february', 'march', 'april',
        'may', 'june', 'july', 'august',
        'september', 'october', 'november', 'december']

UNSEEN = Counter()

class Record(object):
    def __init__(self):
        self.predicates = []

    def add_info(self, line):
        m = re.match(r'iter=(\S*): example (\d+)/\d+: (\S+) {', line)
        if m:
            self.group, self.num, self.ex_id = m.groups()
            return
        m = re.match(r'Tokens: \[(.*)\]$', line)
        if m:
            self.question, = m.groups()
            self.question = self.question.split(', ')
            return
        m = re.match(r'(\$[^(]*)\(([-0-9]*),([-0-9]*)\): ([^{]*) {.*', line)
        if m:
            cat, start, end, formula = m.groups()
            if cat != '$ROOT':
                start, end = int(start), int(end)
                formulas = self.encode(formula)
                if not formulas:
                    return
                if isinstance(formulas, (str, unicode)):
                    formulas = [formulas]
                for formula in formulas:
                    # Get flags
                    flag = self.get_flag(cat, start, end, formula)
                    if flag.startswith('00'):
                        continue
                    if flag[0] == '0':
                        self.predicates.append((flag, formula, self.get_words(formula)))
                    else:
                        self.predicates.append((flag, formula, self.get_words(formula),
                            start, end, ' '.join(self.question[start:end])))
            return
        if line.startswith('$'):
            raise Exception('Unknown line format: %s' % line)

    def encode(self, formula):
        if formula == '(date -1 -1 -1)':
            return None
        # Postfix encoding for specific cases
        if formula == '(fb:type.object.type fb:type.row)':
            return 'type-row'
        if formula.startswith('(number'):
            tokens = formula[1:-1].split()
            assert len(tokens) == 2, '|'.join(tokens)
            return 'N' + tokens[1]
        if formula.startswith('(date'):
            tokens = formula[1:-1].split()
            assert len(tokens) == 4, '|'.join(tokens)
            return 'D' + '-'.join(
                    'XX' if x == '-1' else x for x in tokens[1:4])
        if formula.startswith('(or'):
            tokens = formula.replace('(or ', '').replace(')', '').split()
            # Technically we should output each predicate ...
            return ' '.join([tokens[0]] + [x + ' or' for x in tokens[1:]])
        if formula.startswith('(and'):
            tokens = formula.replace('(', '').replace(')', '').split()
            assert len(tokens) == 7
            assert tokens[1] == '<'
            assert tokens[4] == '>='
            assert tokens[2] == tokens[5] == 'number'
            return ' '.join(['N' + tokens[3], tokens[1],
                'N' + tokens[6], tokens[4], tokens[0]])
        assert not formula.startswith('(')
        if formula.startswith('fb:row.row.'):
            return [formula, '!' + formula]
        return formula

    def get_flag(self, cat, start, end, formula):
        stuff = formula.split()
        # First bit: from the utterance?
        anchored = (start != -1)
        flag = ('1' if anchored else '0')
        # Second bit: from the table?
        if anchored:
            flag += ('1' if any(x.startswith('fb:') for x in stuff) else '0')
        else:
            flag += ('0' if any(x.startswith('fb:cell.cell.') for x in stuff)
                    or formula.replace('!', '') in ('<', '<=', '>', '>=', '=',
                        'type-row', 'fb:row.row.next', 'fb:row.row.index') else '1')
        # Third bit: relation?
        flag += ('1' if formula in ('<', '<=', '>', '>=', '!=')
                or formula.replace('!', '').startswith('fb:cell.cell.')
                or formula.replace('!', '').startswith('fb:row.row.') else '0')
        # Next 6 bits: types
        flag += ('1' if not formula.startswith('fb:cell.cell.') and
                any(x.startswith('fb:cell.') for x in stuff) else '0')
        flag += ('1' if any(x.startswith('fb:part.') for x in stuff) else '0')
        flag += ('1' if formula.startswith('fb:row.row.') else '0')
        flag += ('1' if formula.startswith('!fb:row.row.') else '0')
        flag += ('1' if any(x.startswith('N') for x in stuff) else '0')
        flag += ('1' if any(x.startswith('D') for x in stuff) else '0')
        return flag

    def get_words(self, formula):
        words = []
        for x in formula.split():
            for prefix in ['fb:cell.cell.', 'fb:row.row.', '!fb:row.row.',
                    'fb:cell.', 'fb:part.']:
                if not x.startswith(prefix):
                    continue
                x = x[len(prefix):]
                # TODO: Use original string instead!
                words.extend(x.split('_'))
                break
            else:
                if x[0] == 'N':
                    words.append(x[1:])
                elif x[0] == 'D':
                    if x[1] == '-':
                        # negative year?
                        x = 'D' + x[2:]
                    year, month, day = x[1:].split('-')
                    if year != 'XX':
                        words.append(year)
                    if month != 'XX':
                        words.append(MONTH[int(month)])
                    if day != 'XX':
                        words.append(day)
                else:
                    UNSEEN[x] += 1
        return ' '.join(words)

    def dump(self, args, annotated_data):
        filename = os.path.join(args.outdir, self.ex_id)
        assert not os.path.exists(filename)
        with open(filename, 'w', 'utf8') as fout:
            print >> fout, self.ex_id
            print >> fout, '\t'.join([u' '.join(self.question),
                annotated_data['context'], annotated_data['targetCanon']])
            for stuff in sorted(set(self.predicates)):
                print >> fout, u'\t'.join(unicode(x) for x in stuff)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile',
            help='Input log file')
    parser.add_argument('outdir',
            help='Output directory to create (must NOT exist)')
    parser.add_argument('-a', '--annotated-dir', default='t/annotated/',
            help='Path to annotated directory')
    args = parser.parse_args()

    assert not os.path.exists(args.outdir)
    os.makedirs(args.outdir)

    # Read annotated data
    annotated_data = {}
    dirname = os.path.join(args.annotated_dir, 'data')
    for filename in os.listdir(dirname):
        print >> sys.stderr, 'Reading', filename
        with open(os.path.join(dirname, filename), 'r', 'utf8') as fin:
            header = fin.readline().rstrip('\n').split('\t')
            for line in fin:
                record = dict(zip(header, line.rstrip('\n').split('\t')))
                annotated_data[record['id']] = record
    print >> sys.stderr, 'Read %d annotated records' % len(annotated_data)

    # Process the log
    current_record = None
    num_records = 0
    with open(args.infile, 'r', 'utf8') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('iter='):
                if current_record:
                    current_record.dump(args, annotated_data[current_record.ex_id])
                    if num_records % 1000 == 0:
                        print >> sys.stderr, 'Processed %d ...' % num_records
                num_records += 1
                current_record = Record()
            if current_record:
                current_record.add_info(line)
    current_record.dump(args, annotated_data[current_record.ex_id])
    print >> sys.stderr, 'Finished reading %d records' % num_records
    print UNSEEN

if __name__ == '__main__':
    main()

