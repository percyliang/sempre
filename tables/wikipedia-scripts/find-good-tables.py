#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Find Wikipedia pages with information-rich tables (for Turk task)."""

import sys, os, re, argparse, json, glob, random, shutil
from codecs import open
from collections import defaultdict
from itertools import combinations
from weblib.table import Table

def get_filenames(source_dir):
    return sorted(get_id(x) for x in glob.glob(source_dir + '/*.json'))

def get_id(filename):
    return int(re.search('[0-9]+', filename).group())

def get_alpha(x):
    return re.sub('[^a-z]', '', x.lower())

class Dummy(object):
    """Represent distinct objects. Two Dummy objects are distinct."""
    pass

def get_repeatable_token(x):
    """Numbers <= 999 and blanks are converted into distinct tokens."""
    x = re.sub('[^a-z0-9]', '', x.lower())
    if not x or (x.isdigit() and len(x) <= 3):
        return Dummy()
    return x

def check(page_id, source_dir, criterion):
    # Open the web page
    with open(os.path.join(source_dir, '%d.html' % page_id), 'r', 'utf8') as fin:
        data = fin.read()
    good_tables = []
    for i, table in enumerate(Table.get_wikitable(data, normalization=Table.NORM_CORNER)):
        stat = check_table(table, criterion)
        if stat is not None:
            good_tables.append((i, stat))
    return good_tables

class TableStat(object):
    scores = None

    def __init__(self, table):
        """table should be a weblib.table.Table object."""
        self.num_rows = table.num_rows
        self.num_cols = table.num_cols
        self.num_min = min(table.num_rows, table.num_cols)
        self.num_max = max(table.num_rows, table.num_cols)
        self.num_cells = table.num_cells
        self.num_empty_cells = len([x for x in table.cells if not x[1]])
        self.num_long = len([x for x in table.cells if len(x[1]) >= 40])
        self.num_short_headers = len([x for x in table.rows[0] if len(x) <= 3])
        self.num_numeric_cells = len([x for x in table.cells if re.search('[0-9]', x[1])])
        self.num_repetitive_cols = sum([self.is_repetitive(col) for col in table.cols], 0)
        self.num_similar_colpairs = sum([self.are_similar(col1, col2)
                                         for (col1, col2) in combinations(table.cols, 2)], 0)
        self.num_colspan = len(table.table.find_all(['th', 'td'], attrs={'old-colspan': True}))
        self.num_rowspan = len(table.table.find_all(['th', 'td'], attrs={'old-rowspan': True}))
        self.get_scores()

    def is_repetitive(self, col):
        return len(set(get_repeatable_token(x[1]) for x in col)) < 0.5 * len(col)

    def are_similar(self, col1, col2):
        col1 = set(get_alpha(x[1]) for x in col1) - set([''])
        col2 = set(get_alpha(x[1]) for x in col2) - set([''])
        return len(col1 & col2) >= 3

    def __str__(self):
        return ('[ %3d rows | %2d cols | %4d cells | %2d empty | %2d long | %3d num | %2d rep | %2d sim '
                + '| %2d colspan | %2d rowspan ]') % \
            (self.num_rows, self.num_cols, self.num_cells, self.num_empty_cells, self.num_long,
             self.num_numeric_cells, self.num_repetitive_cols, self.num_similar_colpairs,
             self.num_colspan, self.num_rowspan)

    def get_scores(self):
        scores = []
        # Many rows
        scores.append(sum([self.num_rows >= 10, self.num_rows >= 25], 0))
        # Just-right number of columns
        scores.append((7 - abs(7 - self.num_cols)) / 2)
        # Many numeric columns
        scores.append((self.num_numeric_cells / self.num_rows) ** 0.5)
        # Many repetitive columns
        scores.append(self.num_repetitive_cols)
        # Many similar column pairs
        scores.append(self.num_similar_colpairs ** 0.5)
        scores = [max(0, int(x)) for x in scores]
        self.scores = scores

def is_blacklisted(table):
    # Climate
    first_row = table.table.tr
    if first_row and first_row.get('old-colspan') == '14' and 'Climate data' in first_row.text:
        return True
    return False

def check_table(table, criterion):
    """Return either a TableStat or None."""
    if not table.num_rows:
        return None
    stat = TableStat(table)
    if criterion == 0:
        return stat
    elif criterion == 1:
        if (stat.num_rows >= 8 and stat.num_cols >= 3 and stat.num_empty_cells < 0.3 * stat.num_cells
            and stat.num_numeric_cells > 0.5 * stat.num_rows):
            return stat
    elif criterion == 2:
        if (stat.num_rows >= 8 and stat.num_cols >= 5
            and not is_blacklisted(table)
            and stat.num_empty_cells < 0.3 * stat.num_cells
            and stat.num_numeric_cells > 0.5 * stat.num_rows
            #and stat.num_rowspan < stat.num_min * 0.3
            and stat.num_colspan < stat.num_min * 0.3
            #and stat.num_long < stat.num_min * 0.3
            and stat.num_short_headers < stat.num_cols * 0.3):
            return stat

def dump(i, page_id, table_id, stat, args):
    with open(os.path.join(args.source_dir, '%d.json' % page_id), 'r', 'utf8') as fin:
        meta = json.load(fin)
    meta['tableIndex'] = table_id
    if args.custom_filenames:
        while True:
            try:
                i = int(raw_input('filename for "%s": ' % meta['title']))
                break
            except:
                pass
    with open(os.path.join(args.json_dir, '%d.json' % i), 'w', 'utf8') as fout:
        json.dump(meta, fout)
    if args.copy:
        shutil.copy(os.path.join(args.source_dir, '%d.html' % page_id),
                    os.path.join(args.page_dir, '%d.html' % i))
    else:
        os.symlink(os.path.relpath(os.path.join(args.source_dir, '%d.html' % page_id),
                                   args.page_dir),
                   os.path.join(args.page_dir, '%d.html' % i))
    print '>' * 30, 'Written', i, page_id, table_id, '<' * 30
    print stat

def read_used_pages(json_files, args):
    used_pages = set()
    for filename in json_files:
        with open(os.path.join(args.json_dir, filename)) as fin:
            data = json.load(fin)
            used_pages.add(data['id'])
    print 'Found {} used pages'.format(len(used_pages))
    return used_pages


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-n', '--amount', type=int, default=20,
                        help="number of web pages")
    parser.add_argument('-l', '--limit', type=int, default=100000000,
                        help="limit the number of source web pages")
    parser.add_argument('-s', '--source-dir', default='wikidump.cache/output')
    parser.add_argument('-c', '--criterion', type=int, default=0, choices=(0,1,2),
                        help="criterion for finding tables (higher = more strict)")
    parser.add_argument('-d', '--dry-run', action='store_true',
                        help="do not copy files")
    parser.add_argument('-C', '--copy', action='store_true',
                        help='copy the html file instead of making a symlink')
    parser.add_argument('-i', '--fixed-id', type=int,
                        help="use this fixed page id")
    parser.add_argument('--custom-filenames', action='store_true',
                        help="set custom filename for each file")
    parser.add_argument('-q', '--quick', action='store_true',
                        help='dump the good tables immediately after they are found')
    parser.add_argument('-r', '--resume-quick', action='store_true',
                        help='continue the quick mode')
    parser.add_argument('outprefix')
    args = parser.parse_args()

    quick_index, quick_used_pages = 0, set()
    args.json_dir = os.path.join(args.outprefix + '-json')
    args.page_dir = os.path.join(args.outprefix + '-page')
    if args.resume_quick:
        # Read the highest number in json_dir
        json_files = os.listdir(args.json_dir)
        print 'Found {} JSON files'.format(len(json_files))
        quick_index = max(int(x.replace('.json', '')) for x in json_files) + 1
        quick_used_pages = read_used_pages(json_files, args)
    elif os.path.exists(args.json_dir) or os.path.exists(args.page_dir):
        if raw_input('Path exists. Clobber? ')[0:].lower() != 'y':
            exit(1)
    elif not args.dry_run:
        os.makedirs(args.json_dir)
        os.makedirs(args.page_dir)
        
    if args.fixed_id is not None:
        filenames = [args.fixed_id]
    else:
        filenames = get_filenames(args.source_dir)
    if args.quick:
        filenames = sorted(set(filenames) - set(quick_used_pages))
    print >> sys.stderr, 'Got %d candidates' % len(filenames)
    random.shuffle(filenames)
    filenames = filenames[:args.limit]

    good_tables = []
    for i, page_id in enumerate(filenames):
        if not args.dry_run:
            print '(%d/%d) ===== Checking %d =====' % (i, len(filenames), page_id)
        good_table_ids = check(page_id, args.source_dir, args.criterion)
        if good_table_ids:
            good_tables.append((page_id, good_table_ids))
            if args.dry_run:
                print '>' * 30, page_id, '<' * 30
                with open(os.path.join(args.source_dir, '%d.json' % page_id), 'r', 'utf8') as fin:
                    meta = json.load(fin)
                print meta
                for table_id, stat in good_table_ids:
                    if stat.scores:
                        print '%2d' % sum(stat.scores),
                        print 'SCORE %9d (ID %9d) table %2d' % (page_id, meta['id'], table_id),
                        print stat, stat.scores
            if args.quick:
                for table_id, stat in good_table_ids:
                    dump(quick_index, page_id, table_id, stat, args)
                    quick_index += 1

    if args.quick:
        return

    random.shuffle(good_tables)
    good_tables = good_tables[:args.amount]
    if args.dry_run:
        return

    for i, (page_id, good_table_ids) in enumerate(good_tables):
        table_id, stat = random.choice(good_table_ids)
        dump(i, page_id, table_id, stat, args)

if __name__ == '__main__':
    main()
