#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Extract categories and their members from Wikipedia dump."""

import sys, os, re, argparse, json, gzip
from codecs import open
from collections import defaultdict

HIDDEN_CAT_CAT = 15961454

################################################################
# List all pages in category 0 or 4
# Output: bimap between ID and (namespace, name)
def get_name_bimap():
    id_to_name, name_to_id = {}, {}
    with gzip.open('pages-filtered.tsv.gz') as fin:
        for i, line in enumerate(fin):
            if i % 1000000 == 0:
                print >> sys.stderr, 'Processing %d ...' % i
            line = line[:-1].split('\t')
            _id = int(line[0])
            _name = (int(line[1]), line[2])
            id_to_name[_id] = _name
            name_to_id[_name] = _id
    print >> sys.stderr, 'Found %d pages' % len(id_to_name)
    return id_to_name, name_to_id

def read_hidden_cats():
    with open('hidden-cats') as fin:
        hidden_cats = set(int(x) for x in fin)
    hidden_cats.add(HIDDEN_CAT_CAT)
    print >> sys.stderr, 'Found %d hidden cats' % len(hidden_cats)
    return hidden_cats

################################################################
# List all (page, category) pairs
# Output: mapping from cat id to page ids
def get_cat_to_pages(id_to_name, name_to_id):
    cat_to_pages = defaultdict(list)
    with gzip.open('links-filtered.tsv.gz') as fin:
        for i, line in enumerate(fin):
            if i % 1000000 == 0:
                print >> sys.stderr, 'Reading %d ...' % i
            line = line[:-1].split('\t')
            page = int(line[0])
            cat = int(line[1])
            if page in id_to_name and cat in id_to_name:
                cat_to_pages[cat].append(page)
    return cat_to_pages

# Pre-process links.tsv.gz
def dump_page_cat_pairs(id_to_name, name_to_id, hidden_cats):
    with gzip.open('links.tsv.gz') as fin:
        with gzip.open('links-filtered.tsv.gz', 'w') as fout:
            num_found = 0
            for i, line in enumerate(fin):
                if i % 1000000 == 0:
                    print >> sys.stderr, 'Reading %d (%d found so far) ...' % \
                        (i, num_found)
                line = line[:-1].split('\t')
                try:
                    page = int(line[0])
                    cat = name_to_id[(14, line[1])]
                    if page in id_to_name and cat not in hidden_cats:
                        fout.write('%d\t%d\n' % (page, cat))
                        num_found += 1
                except LookupError:
                    pass

################################################################
# Only keep useful categories
def filter_cats(cat_to_pages, name_to_id):
    print >> sys.stderr, 'Before: %d cats' % len(cat_to_pages)
    for cat in cat_to_pages.keys():
        if len(cat_to_pages[cat]) < 3:
            del cat_to_pages[cat]
    print >> sys.stderr, ' After: %d cats' % len(cat_to_pages)

################################################################
# Dump to file
def dump(cat_to_pages, id_to_name):
    with gzip.open('grouped.txt.gz', 'w') as fout:
        for i, (cat, pages) in enumerate(cat_to_pages.iteritems()):
            if i % 100000 == 0:
                print >> sys.stderr, 'Dumping %d ...' % i
            fout.write(str(cat) + '\t' + id_to_name[cat][1] + '\n')
            fout.write(str(len(pages)) + '\n')
            for page in pages:
                fout.write(str(page) + '\t' + id_to_name[page][1] + '\n')
            fout.write('\n')

def main():
    parser = argparse.ArgumentParser()
    args = parser.parse_args()
    id_to_name, name_to_id = get_name_bimap()
    ################
    #hidden_cats = read_hidden_cats()
    #dump_page_cat_pairs(id_to_name, name_to_id, hidden_cats)
    #exit(0)
    ################
    cat_to_pages = get_cat_to_pages(id_to_name, name_to_id)
    filter_cats(cat_to_pages, name_to_id)
    dump(cat_to_pages, id_to_name)

if __name__ == '__main__':
    main()
