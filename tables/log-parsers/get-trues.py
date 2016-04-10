#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile')
    args = parser.parse_args()

    state = None

    with open(args.infile, 'r', 'utf8') as fin:
        for line in fin:
            line = line.rstrip()
            if line.strip().startswith('Example: '):
                state = 'started'
                print
                print '#' * 200
                print
                print line
            elif state == 'started':
                print line
                if line.strip() == '}':
                    state = None
            elif line.strip().startswith('True@'):
                print line

if __name__ == '__main__':
    main()

