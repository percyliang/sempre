#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

class Record(object):
    def __init__(self):
        pass

    def add_info(self, line):
        m = re.match(r'iter=(\S*): example (\d+)/\d+: (\S+) {', line)
        if m:
            self.group, self.num, self._id = m.groups()
            return
        m = re.match(r'Example: (.*) {', line)
        if m:
            self.question, = m.groups()
            return
        m = re.match(r'context: \(context \(graph tables\.TableKnowledgeGraph (csv/\d+-csv/\d+.csv)\)\)', line)
        if m:
            self.context, = m.groups()
            return
        m = re.match('targetValue: (.*)', line)
        if m:
            self.targets, = m.groups()
            return
        m = re.match('Current: correct=([0-9.]+) oracle=([0-9.]+) .*', line)
        if m:
            self.correct, self.oracle = str(int(float(m.group(1)))), str(int(float(m.group(2))))
            return
        m = re.match('(True|Pred)@\d{4}: .*', line)
        if m:
            return
        raise Exception('Unknown line format: %s' % line)

    def dump(self, args):
        if args.dev_only and not self.group.endswith('dev'):
            return
        stuff = [self.group, self.num, self._id, self.correct, self.oracle]
        if args.verbose:
            stuff.extend([self.question, self.context, self.targets])
        print '\t'.join(stuff)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    parser.add_argument('-d', '--dev-only', action='store_true')
    parser.add_argument('-v', '--verbose', action='store_true')
    args = parser.parse_args()

    current_record = None
    num_records = 0
    with open(args.infile, 'r', 'utf8') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('iter='):
                if current_record:
                    current_record.dump(args)
                    if num_records % 1000 == 0:
                        print >> sys.stderr, 'Processed %d ...' % num_records
                num_records += 1
                current_record = Record()
            current_record.add_info(line)
    current_record.dump(args)
    print >> sys.stderr, 'Finished reading %d records' % num_records

if __name__ == '__main__':
    main()

