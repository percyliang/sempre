#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Get Wikipedia markup and metadata from Wikipedia dump."""

import sys, os, re, argparse, json, bz2
from codecs import open
from collections import defaultdict

import HTMLParser
html_parser = HTMLParser.HTMLParser()

def is_sane(content, args):
    if content.startswith('#REDIRECT'):
        return False
    if args.table and ('{|' not in content or 'wikitable' not in content):
        return False
    return True

OUTSIDE = 0
INSIDE = 1
CONTENT = 2

def get_raw(fin, args):
    """Generate (id, title, content)."""
    state = OUTSIDE
    id_, ns, title, revision, content = None, None, None, None, None
    i = 0
    while True:
        line = fin.readline()
        if not line:
            break
        i += 1
        if i % 50000 == 0:
            print >> sys.stderr, 'Reading line %d' % i
        if state == OUTSIDE:
            if line.startswith('  <page>'):
                state = INSIDE
        elif state == INSIDE:
            if line.startswith('    <title>'):
                title = line[11:-9]
            elif line.startswith('    <id>'):
                id_ = int(line[8:-6])
            elif line.startswith('    <ns>'):
                ns = int(line[8:-6])
            elif line.startswith('      <id>'):
                revision = int(line[10:-6])
            elif line.startswith('      <text xml:space="preserve">'):
                if line.endswith('</text>\n'):
                    content = [line[33:-8]]
                else:
                    content = [line[33:]]
                    state = CONTENT
            elif line.startswith('  </page>'):
                if args.markup:
                    content = ''.join(content).decode('utf8')
                    content = html_parser.unescape(content)
                else:
                    content = ''.join(content)
                if ns == 0 and is_sane(content, args):
                    meta = {'id': id_, 'revision': revision, 'title': title}
                    yield meta, content
                state = OUTSIDE
        else:
            if line.endswith('</text>\n'):
                content.append(line[:-8] + '\n')
                state = INSIDE
            else:
                content.append(line)

def output(record, args):
    meta, content = record
    filename = os.path.join(args.out, str(meta['id']))
    with open(filename + '.markup', 'w', 'utf8') as fout:
        fout.write(content)
    with open(filename + '.meta', 'w', 'utf8') as fout:
        json.dump(meta, fout)

def output_combine(fout, record, args):
    json.dump(record[0], fout)
    fout.write('\n')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('out', help='output destination')
    parser.add_argument('-i', '--infile',
                        default='enwiki-20140402-pages-articles.xml.bz2')
    parser.add_argument('-l', '--limit', type=int, default=100,
                        help='limit the number of outputs')
    parser.add_argument('-t', '--table', action='store_true', default=False,
                        help='only output pages with a wikitable')
    parser.add_argument('-m', '--markup', action='store_true', default=False,
                        help='also output *.markup')
    args = parser.parse_args()

    if not os.path.exists(args.out) and args.markup:
        os.makedirs(args.out)

    if args.markup:
        with bz2.BZ2File(args.infile) as fin:
            for i, record in enumerate(get_raw(fin, args)):
                output(record, args)
                if i + 1 >= args.limit:
                    break
    else:
        with open(args.out, 'w', 'utf8') as fout:
            with bz2.BZ2File(args.infile) as fin:
                for i, record in enumerate(get_raw(fin, args)):
                    output_combine(fout, record, args)
                    if i + 1 >= args.limit:
                        break
                    if (i + 1) % 500 == 0:
                        print 'Got %d pages!' % (i + 1)

if __name__ == '__main__':
    main()
