#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-f', '--field', type=int, default=4)
    parser.add_argument('infile')    
    args = parser.parse_args()

    firsts = []
    seconds = []

    with open(args.infile, 'r', 'utf8') as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('Reading Example'):
                firsts.append([])
                seconds.append([])
            elif line.startswith('(FIRST)') or line.startswith('(SECOND)'):
                line = line.split() 
                if line[1].isdigit():
                    amount = int(line[args.field])
                    if line[0] == '(FIRST)':
                        firsts[-1].append(amount)
                    elif line[0] == '(SECOND)':
                        seconds[-1].append(amount)
                    else:
                        raise Exception('Huh?')
    print >> sys.stderr, 'Read', len(firsts), 'examples'

    # Pad
    def pad(stuff):
        n = max(len(x) for x in stuff)
        for y in stuff:
            while len(y) < n:
                y.append(y[-1])
    pad(firsts)
    pad(seconds)

    # Find statistics
    def stat(stuff):
        if not stuff:
            return
        answer = []
        for i in xrange(len(stuff[0])):
            data = [x[i] for x in stuff]
            data.sort()
            n = len(data)
            mean = sum(float(x) for x in data) / n
            var = sum(float(x) * float(x) for x in data) / n - mean * mean
            print '{}: N={} mean={:.2f} sd={:.2f} min={:d} q1={:d} q2={:d} q3={:d} max={:d}'.format(
                i, n, mean, var**.5, data[0], data[n/4], data[n/2], data[3*n/4], data[-1])
            answer.append([data[n/4], data[n/2], data[3*n/4]])
        return zip(*answer)
    print 'FIRST:'
    answer_first = stat(firsts)
    print 'SECOND:'
    answer_second = stat(seconds)

    # Plot
    import matplotlib.pyplot as plt

    fig = plt.figure()
    ax1 = fig.add_subplot(211)
    if answer_first:
        for series in answer_first:
            print series
            ax1.plot(series)
    #for stuff in firsts:
    #    ax1.plot(stuff)
    ax2 = fig.add_subplot(212)
    if answer_second:
        for series in answer_second:
            print series
            ax2.plot(series)
    #for stuff in seconds:
    #    ax2.plot(stuff)
    plt.show()


if __name__ == '__main__':
    main()

