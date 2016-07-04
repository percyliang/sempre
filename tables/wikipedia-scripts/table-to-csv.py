#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, re, argparse, json
from codecs import open
from collections import defaultdict
from weblib.table import Table
from itertools import izip_longest

################ Dump CSV

def simple_normalize_text(text):
    return text.replace('\\', '\\\\').replace('"', r'\"').replace('\n', r'\n').replace(u'\xa0', ' ').strip()

def dump_csv(rows, fout):
    for row in rows:
        fout.write(','.join('"%s"' % simple_normalize_text(x) for x in row) + '\n')

def tab_normalize_text(text):
    return re.sub(r'\s+', ' ', text.replace('\n', r'\n'), re.U).strip()

def dump_tsv(rows, fout):
    for row in rows:
        fout.write('\t'.join('%s' % tab_normalize_text(x) for x in row) + '\n')

################ More table normalization

def debug_print(stuff):
    for x in stuff:
        print >> sys.stderr, [simple_normalize_text(y) for y in x]

def transpose(rows):
    cols = []
    n = max(len(row) for row in rows)
    for i in xrange(n):
        col = []
        for row in rows:
            try:
                col.append(row[i])
            except LookupError:
                col.append(None)
        cols.append(col)
    return cols

def anti_transpose(cols):
    # All col in cols must have equal length
    assert len(set(len(col) for col in cols)) == 1
    rows = []
    n = len(cols[0])
    for i in xrange(n):
        row = []
        for col in cols:
            if col[i] is not None:
                row.append(col[i])
            else:
                row.append('')
        rows.append(row)
    return rows

def remove_full_rowspans(rows):
    """Remove rows in which all cells have the same text."""
    return [row for row in rows if len(set(row)) > 1]

def remove_empty_columns(orig_cols):
    """Remove columns with <= 1 non-empty cells."""
    cols = []
    for col in orig_cols:
        non_empty = sum((bool(cell) for cell in col), 0)
        if non_empty >= 2:
            cols.append(col)
    return cols

#### Merge columns

def are_mergeable(col1, col2):
    assert len(col1) == len(col2)
    merged = []
    for i in xrange(len(col1)):
        c1, c2 = col1[i], col2[i]
        if not c1:
            merged.append(c2)
        elif not c2 or c1 == c2:
            merged.append(c1)
        else:
            return None
    return merged

def merge_similar_columns(orig_cols):
    """Merge similar columns."""
    i = 0
    while i + 1 < len(orig_cols):
        merged = are_mergeable(orig_cols[i], orig_cols[i+1])
        if merged is not None:
            orig_cols[i:i+2] = [merged]
        else:
            i += 1
    return orig_cols

#### Split columns

REGEX_NEWLINE = re.compile(ur'^([^\n]*)\n([^\n]*)$', re.U | re.DOTALL)
REGEX_NEWLINE_PAREN = re.compile(ur'^(.*)\n(\(.*\))$', re.U | re.DOTALL)
REGEX_SPLIT_PAREN = re.compile(ur'^(.*) +(\(.*\))$', re.U | re.DOTALL)

def split_multiline_columns(orig_cols):
    """Split columns with newline in each cell."""
    i = 0
    while i < len(orig_cols):
        for regex, threshold in ((REGEX_NEWLINE, 0.5 * len(orig_cols[i])),
                                 (REGEX_NEWLINE_PAREN, 1),
                                 (REGEX_SPLIT_PAREN, 2)):
            matches = [regex.match(cell or '') for cell in orig_cols[i]]
            num_matches = sum((bool(match) for match in matches[1:]), 0)
            if num_matches >= threshold:
                splitted = [((None, None) if cell is None
                             else (cell, '') if not match
                             else (match.group(1), match.group(2)))
                            for (cell, match) in zip(orig_cols[i], matches)]
                orig_cols[i:i+1] = [[(None if x[0] is None else x[0].strip()) for x in splitted],
                                    [(None if x[1] is None else x[1].strip()) for x in splitted]]
                if not orig_cols[i+1][0]:
                    orig_cols[i+1][0] = (orig_cols[i][0] or '') + '#n'
                break
        i += 1
    return orig_cols

REGEX_FROM_TO = re.compile(ur'^(.*)[-–—‒―](.*)$', re.U)

def split_from_to_columns(orig_cols):
    """Split columns with pattern '... - ...'."""
    i = 0
    while i < len(orig_cols):
        matches = [REGEX_FROM_TO.match(cell or '') for cell in orig_cols[i]]
        num_matches = sum((bool(match) for match in matches), 0)
        if num_matches > 0.5 * len(orig_cols[i]):
            splitted = [((None, None) if cell is None
                         else (cell, '') if not match
                         else (match.group(1), match.group(2)))
                        for (cell, match) in zip(orig_cols[i], matches)]
            orig_cols[i:i+1] = [[(None if x[0] is None else x[0].strip()) for x in splitted],
                                [(None if x[1] is None else x[1].strip()) for x in splitted]]
            if not orig_cols[i+1][0]:
                orig_cols[i+1][0] = orig_cols[i][0]
            orig_cols[i][0] += '#f'
            orig_cols[i+1][0] += '#t'
        i += 1
    return orig_cols

#### Normalize by column

REGEX_NUMBERING = re.compile(ur'^([0-9]+)[.)]$', re.U)
REGEX_REFERENCE = re.compile(ur'^(.*)[‡^†*]+$', re.U)
REGEX_PARENS = re.compile(ur'^\((.*)\)$', re.U)
REGEX_QUOTES = re.compile(ur'^"(.*)"$', re.U)

def normalize_common_punctuations(orig_cols):
    """Normalize some punctuations if they appear a lot in the same row."""
    cols = []
    for col in orig_cols:
        for regex, threshold in ((REGEX_NUMBERING, 0.8 * len(col)),
                                 (REGEX_REFERENCE, 0.2 * len(col)),
                                 (REGEX_PARENS, 0.5 * len(col)),
                                 (REGEX_QUOTES, 0.5 * len(col))):
            matches = [regex.match(cell or '') for cell in col]
            num_matches = sum((not cell or bool(match)
                               for (cell, match) in zip(col, matches)), 0)
            if num_matches >= threshold:
                col = [(cell if not match else match.group(1))
                       for (cell, match) in zip(col, matches)]
        cols.append(col)
    return cols

#### Normalize by cell

REGEX_COMMA = re.compile(ur'([0-9]),([0-9][0-9][0-9])', re.U)

def remove_commas_from_single_number(x):
    if not x:
        return x
    while REGEX_COMMA.search(x):
        x = REGEX_COMMA.sub(r'\1\2', x)
    return x

def remove_commas_from_numbers(orig_stuff):
    stuff = []
    for slab in orig_stuff:
        stuff.append([remove_commas_from_single_number(x) for x in slab])
    return stuff

################ Main function

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-s', '--source-dir', default='wikidump.cache/output',
                        help="source directory")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-j', '--json',
                        help="json metadata file specifying page and table id")
    group.add_argument('-J', '--turk-json',
                        help="json metadata file from MTurk task")
    group.add_argument('-p', '--page-id', type=int,
                        help="page index")
    parser.add_argument('-t', '--table-id', type=int, default=0,
                        help="table index (only used for -p / --page-id)")
    parser.add_argument('-o', '--outfile',
                        help="output filename (default = stdout)")
    parser.add_argument('-n', '--normalize', action='count',
                        help='degree of normalization')
    parser.add_argument('--tsv', action='store_true',
                        help='output TSV instead of CSV')
    args = parser.parse_args()

    if args.json:
        with open(args.json) as fin:
            metadata = json.load(fin)
            args.page_id = metadata['id']
            args.table_id = metadata['tableIndex']
        inhtml = os.path.join(args.source_dir, '%d.html' % args.page_id)
    elif args.turk_json:
        with open(args.turk_json) as fin:
            metadata = json.load(fin)
            args.page_id = metadata['id']
            args.table_id = metadata['tableIndex']
        # The following replacement is pretty hacky:
        inhtml = args.turk_json.replace('-json', '-page').replace('.json', '.html')
    else:
        inhtml = os.path.join(args.source_dir, '%d.html' % args.page_id)

    with open(inhtml, 'r', 'utf8') as fin:
        table = Table.get_wikitable(fin.read(), args.table_id, normalization=Table.NORM_DUPLICATE)
    rows = table.rows
    if args.normalize >= 1:
        # Remove redundant rows and columns
        rows = remove_full_rowspans(rows)
        cols = transpose(rows)
        cols = remove_empty_columns(cols)
        cols = merge_similar_columns(cols)
        rows = anti_transpose(cols)
    if args.normalize >= 2:
        # Split cells / Normalize texts
        cols = transpose(rows)
        cols = split_multiline_columns(cols)
        cols = normalize_common_punctuations(cols)
        cols = split_from_to_columns(cols)
        cols = remove_commas_from_numbers(cols)
        rows = anti_transpose(cols)
    #debug_print(transpose(rows))
    outputter = dump_tsv if args.tsv else dump_csv
    if not args.outfile:
        outputter(rows, sys.stdout)
    else:
        with open(args.outfile, 'w', 'utf8') as fout:
            outputter(rows, fout)

if __name__ == '__main__':
    main()
