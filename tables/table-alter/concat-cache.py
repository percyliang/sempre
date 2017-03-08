#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('id_range', type=int, nargs=2)
    parser.add_argument('-d', '--basedir', default='altered')
    parser.add_argument('-o', '--outfile')
    args = parser.parse_args()

    if args.outfile and os.path.exists(args.outfile):
        if raw_input('Delete {}? (y/N): '.format(args.outfile))[0:].lower() != 'y':
            return
        os.remove(args.outfile)

    if args.outfile:
        fout = open(args.outfile, 'w', 'utf8')
    else:
        fout = sys.stdout

    num_ex = num_files = 0
    for i in xrange(args.id_range[0], args.id_range[1] + 1):
        print >> sys.stderr, 'Processing', i
        basedir = os.path.join(args.basedir, 'nt-' + str(i))
        j = 1
        while True:
            infile = os.path.join(basedir, str(j) + '.tsv')
            if not os.path.exists(infile):
                break
            with open(infile, 'r', 'utf8') as fin:
                data = fin.readlines()
                print >> fout, '{}\t{}\t{}'.format('nt-' + str(i), j, len(data))
                for line in data:
                    fout.write(line)
            j += 1
            num_files += 1
        num_ex += 1

    if args.outfile:
        fout.close()
    print >> sys.stderr, 'Written {} files ({} examples)'.format(num_files, num_ex)
    

if __name__ == '__main__':
    main()

