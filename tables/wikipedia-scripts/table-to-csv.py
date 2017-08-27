#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Convert HTML table into CSV / TSV / pretty-printed table."""

import sys, os, re, argparse, json
from codecs import open
from collections import defaultdict
from weblib.table import Table
from itertools import izip_longest

################ Dump CSV

def simple_normalize_text(text):
    return text.replace('\\', '\\\\').replace('"', r'\"').replace('\n', r'\\n').replace(u'\xa0', ' ').strip()

def dump_csv(rows, fout):
    for row in rows:
        fout.write(','.join('"%s"' % simple_normalize_text(x[1]) for x in row) + '\n')

def tab_normalize_text(text):
    return re.sub(r'\s+', ' ', text.replace('\\', '\\\\').replace('|', r'\p').replace('\n', r'\n'), re.U).strip()

def dump_tsv(rows, fout):
    for row in rows:
        fout.write('\t'.join('%s' % tab_normalize_text(x[1]) for x in row) + '\n')

def table_normalize_text(text):
    return re.sub(r'\s+', ' ', text, re.U).strip()

def dump_table(rows, fout):
    widths = defaultdict(int)
    for row in rows:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(table_normalize_text(cell[1])) + 1)
    for row in rows:
        fout.write('|')
        for i, cell in enumerate(row):
            # wow this is so hacky
            fout.write((' %-' + str(widths[i]) + 's') % table_normalize_text(cell[1]))
            fout.write('|')
        fout.write('\n')

################ More table normalization

def debug_print(stuff):
    for x in stuff:
        print >> sys.stderr, [simple_normalize_text(y[1]) for y in x]

def transpose(rows):
    cols = []
    n = max(len(row) for row in rows)
    for i in xrange(n):
        col = []
        for row in rows:
            try:
                col.append(row[i])
            except LookupError:
                col.append(('', ''))
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
                row.append(('', ''))
        rows.append(row)
    return rows

def remove_full_rowspans(rows):
    """Remove rows in which all cells have the same text."""
    return [row for row in rows if len(set(row)) > 1]

def remove_empty_columns(orig_cols):
    """Remove columns with <= 1 non-empty cells."""
    cols = []
    for col in orig_cols:
        non_empty = sum((bool(cell[1]) for cell in col), 0)
        if non_empty >= 2:
            cols.append(col)
    return cols

#### Merge columns

def are_mergeable(col1, col2):
    assert len(col1) == len(col2)
    merged = []
    for i in xrange(len(col1)):
        c1, c2 = col1[i], col2[i]
        if not c1[1]:
            merged.append(c2)
        elif not c2[1] or c1 == c2:
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

#### Merge header rows

def merge_header_rows(orig_rows):
    """Merge all header rows together."""
    header_rows, body_rows = [], []
    still_header = True
    for row in orig_rows:
        if not still_header or any(cell[0] == 'td' for cell in row):
            still_header = False
            body_rows.append(row)
        else:
            header_rows.append(row)
    if len(header_rows) < 2 or not body_rows:
        return orig_rows
    # Merge header rows with '\n'
    header_cols = transpose(header_rows)
    header_row = []
    for col in header_cols:
        texts = [None]
        for cell in col:
            if cell[1] != texts[-1]:
                texts.append(cell[1])
        header_row.append(('th', '\n'.join(texts[1:])))
    return [header_row] + body_rows

################ Main function

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-j', '--turk-json',
                        help="json metadata file from MTurk task")
    parser.add_argument('-o', '--outfile',
                        help="output filename (default = stdout)")
    parser.add_argument('--tsv', action='store_true',
                        help='also print out tsv')
    parser.add_argument('--human', action='store_true',
                        help='also print out human-readable table')
    parser.add_argument('--html', action='store_true',
                        help='also print out cleaned html for the table')
    parser.add_argument('--keep-hidden', action='store_true',
                        help='keep hidden texts as is')
    args = parser.parse_args()
    assert not args.tsv or args.outfile.endswith('.csv')

    with open(args.turk_json) as fin:
        metadata = json.load(fin)

    # Get the path to the HTML file
    # This is kind of hacky
    match = re.match(r'^(?:json|page)/(\d+)-(?:json|page)/(\d+).json$', args.turk_json)
    batch_id, data_id = match.groups()
    inhtml = 'page/{}-page/{}.html'.format(batch_id, data_id)

    with open(inhtml, 'r', 'utf8') as fin:
        raw = fin.read()
    table = Table.get_wikitable(raw, metadata['tableIndex'],
            normalization=Table.NORM_DUPLICATE,
            remove_hidden=(not args.keep_hidden))
    if args.html:
        raw_table = Table.get_wikitable(raw, metadata['tableIndex'],
                remove_hidden=False).table

    rows = table.rows
    # rows = list of columns; column = list of cells; cell = (tag, text)
    # Remove redundant rows and columns
    rows = remove_full_rowspans(rows)
    cols = transpose(rows)
    cols = remove_empty_columns(cols)
    cols = merge_similar_columns(cols)
    rows = anti_transpose(cols)
    rows = merge_header_rows(rows)
    # Dump
    if not args.outfile:
        dump_csv(rows, sys.stdout)
    else:
        stem = re.sub('\.csv$', '', args.outfile)
        with open(args.outfile, 'w', 'utf8') as fout:
            dump_csv(rows, fout)
        if args.tsv:
            with open(stem + '.tsv', 'w', 'utf8') as fout:
                dump_tsv(rows, fout)
        if args.human:
            with open(stem + '.table', 'w', 'utf8') as fout:
                dump_table(rows, fout)
        if args.html:
            with open(stem + '.html', 'w', 'utf8') as fout:
                print >> fout, unicode(raw_table)

if __name__ == '__main__':
    main()
