#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def read_num_logical_forms(dirname):
    with open(os.path.join(dirname, 'output', 'output.map')) as fin:
        for line in fin:
            line = line.strip().split('\t')
            if line[0] == 'train.numCandidates.mean':
                return float(line[1])

def read_oracle(dirname):
    count = 0
    with open(os.path.join(dirname, 'output', 'checker.results')) as fin:
        for line in fin:
            line = line.strip().split('\t')
            if line[2] == 'yes':
                count += 1
    return count

def read_single_dir(dirname):
    return (read_num_logical_forms(dirname), read_oracle(dirname) / 300.)

def read_multiple_dirs(dirnames):
    num_logical_forms = 0.0
    oracle = 0
    for dirname in dirnames:
        num_logical_forms += read_num_logical_forms(dirname)
        oracle += read_oracle(dirname)
    return (num_logical_forms / len(dirnames), oracle / 300.)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-p', '--plot', action='store_true',
            help='plot the numbers (requires matplotlib)')
    parser.add_argument('-o', '--plotout',
            help='output the plot to a file instead of displaying it')
    parser.add_argument('-d', '--debug', action='store_true',
            help='debug mode (only beam sizes 20 and 1000)')
    args = parser.parse_args()
    
    # Read data
    beam_uninit = []
    beam_guided = []
    sizes = (20, 50, 100, 200, 500, 1000, 2000, 5000, 10000)
    if args.debug:
        sizes = (20, 1000)
    for beamsize in sizes:
        if beamsize <= 500:
            beam_uninit.append(read_single_dir('beam-%d' % beamsize))
            beam_guided.append(read_single_dir('beam-%d-guided' % beamsize))
        else:
            beam_uninit.append(read_multiple_dirs([
                'beam-%d%s' % (beamsize, x) for x in 'abc']))
            beam_guided.append(read_multiple_dirs([
                'beam-%d%s-guided' % (beamsize, x) for x in 'abc']))
    print beam_uninit
    print beam_guided
    beam_uninit_x = [x for (x, y) in beam_uninit]
    beam_uninit_y = [y for (x, y) in beam_uninit]
    beam_guided_x = [x for (x, y) in beam_guided]
    beam_guided_y = [y for (x, y) in beam_guided]

    dpd_x, dpd_y = read_multiple_dirs([
        'check-000-099', 'check-100-199', 'check-200-299'])

    if args.plot:
        import matplotlib
        if args.plotout:
            # Allow matplotlib to run without X server
            matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        plt.xlabel('number of final LFs produced')
        plt.ylabel('annotated LFs coverage')
        plt.plot(beam_uninit_x, beam_uninit_y, 'b--')
        plt.plot(beam_guided_x, beam_guided_y, 'r')
        plt.plot(dpd_x, dpd_y, 'g*')
        if args.plotout:
            plt.savefig(args.plotout)
        else:
            plt.show()

if __name__ == '__main__':
    main()

