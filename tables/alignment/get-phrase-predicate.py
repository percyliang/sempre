#!/usr/bin/env python
# -*- coding: utf-8 -*-

# Dump phrase-predicate pairs
# Expected environment: DPParser where cheat is on and training is off
# ./run @mode=tables @parser=dpsize -cheat

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

class Example(object):
    i = -1

    def __init__(self, line):
        match = re.match(r'iter=0.train: example [0-9]*/[0-9]*: (.*) {', line)
        self.example_id = match.group(1)
        Example.i += 1
        if Example.i % 10 == 0:
            sys.stderr.write('Reading Example %d ...\r' % Example.i)
        self.formulas = []

    def add_tokens(self, line):
        match = re.match(r'[^:]*: \[(.*)\]', line)
        self.tokens = match.group(1).split(', ')

    def add_formula(self, line, keep_parentheses=False):
        match = re.match(r'True.*\(formula (.*) \(value', line)
        if keep_parentheses:
            predicates = match.group(1).replace('(', ' ( ').replace(')', ' ) ')
            predicates = predicates.strip().split()
        else:
            predicates = re.sub(r'[()]', '', match.group(1)).split()
        self.formulas.append(predicates)

    def dump(self):
        tokens = ' '.join(self.tokens)
        for i, predicates in enumerate(self.formulas):
            print '\t'.join([self.example_id, str(len(self.formulas)),
                tokens, ' '.join(predicates)])

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('logfile')
    parser.add_argument('-l', '--lemmatized', action='store_true',
                        help='Use the lemmatized form of the utterance')
    parser.add_argument('-p', '--parentheses', action='store_true',
                        help='Keep parentheses in the formula')
    args = parser.parse_args()
    
    current_example = None

    with open(args.logfile, 'r', 'utf8') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('iter=0.train'):
                # Begin a new example
                if current_example:
                    current_example.dump()
                current_example = Example(line)
            elif line.startswith('Tokens:'):
                if not args.lemmatized:
                    current_example.add_tokens(line)
            elif line.startswith('Lemmatized tokens:'):
                if args.lemmatized:
                    current_example.add_tokens(line)
            elif line.startswith('True@'):
                current_example.add_formula(line, args.parentheses)
    if current_example:
        current_example.dump()
    print >> sys.stderr
    print >> sys.stderr, 'DONE!'

if __name__ == '__main__':
    main()

