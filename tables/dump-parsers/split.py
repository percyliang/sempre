#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Split gzip files generated from SerializedDumper
into multiple files, each with only a single example.

The filenames are the example IDs.
"""

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

import glob, gzip

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('destdir')
    parser.add_argument('indirs', nargs='+')
    args = parser.parse_args()

    for i, indir in enumerate(sorted(args.indirs)):
        print >> sys.stderr, 'INDIR {:2}: {}'.format(i, indir)

    if os.path.exists(args.destdir):
        if raw_input('Delete {} directory? (y/N): '.format(args.destdir))[0:].lower() != 'y':
            return
        shutil.rmtree(args.destdir)
    os.makedirs(args.destdir)

    filenames = []
    for indir in args.indirs:
        filenames.extend(glob.glob(os.path.join(indir, '*.gz')))
    print >> sys.stderr, len(filenames), 'files.'
    
    count = 0
    for filename in sorted(filenames):
        print >> sys.stderr, 'Processing', filename
        with gzip.open(filename) as fin:
            fout = None
            metadata = fin.readline()
            group = re.search(r'\(group ([^)]*)\)', metadata).group(1)
            for line in fin:
                if line.startswith('#'):
                    tokens = line.strip().split()
                    assert tokens[1] == 'Example'
                    ex_id = tokens[2]
                    if fout:
                        fout.close()
                    new_filename = 'dumped-single_{}-{:06d}-{}.gz'.format(group, count, ex_id)
                    fout = gzip.open(os.path.join(args.destdir, new_filename), 'wb')
                    print >> fout, '(metadata (group {}) (offset {}) (size 1))'.format(group, count)
                    count += 1
                if fout:
                    fout.write(line)
            if fout:
                fout.close()
            print >> sys.stderr, 'Written', count, 'files'

if __name__ == '__main__':
    main()

