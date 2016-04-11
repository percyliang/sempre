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

    enabled = False
    with open(args.infile, 'r', 'utf8') as fin:
        for line in fin:
            line = line.strip()
            if line.strip().startswith('Example: '):
                enabled = True
            elif line.startswith('True@') and enabled:
                print line
                enabled = False

if __name__ == '__main__':
    main()

