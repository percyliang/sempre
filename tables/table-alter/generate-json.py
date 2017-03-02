#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys, os, shutil, re, argparse, json, shutil
from codecs import open
from itertools import izip
from collections import defaultdict

def read_dataset(filename):
    with open(filename, 'r', 'utf8') as fin:
        header = fin.readline().rstrip('\n').split('\t')
        dataset = {}
        for line in fin:
            example = dict(zip(header, line.rstrip('\n').split('\t')))
            example['targetValue'] = example['targetValue'].split('|')
            dataset[example['id']] = example
    print >> sys.stderr, 'Read {} examples'.format(len(dataset))
    return dataset

class RetainedTablesData(object):
    def __init__(self, ex_id, alter_id, score, other_alter_ids):
        self.ex_id = ex_id
        self.alter_id = int(alter_id)
        self.score = round(float(score), 3)
        self.other_alter_ids = [int(x) for x in other_alter_ids]

def read_retained(filename):
    """Format: ID <tab> score <tab> altered table ids"""
    retained = []
    with open(filename) as fin:
        for line in fin:
            ex_id, score, alter_ids = line.rstrip().split('\t')
            alter_ids = alter_ids.split()
            for alter_id in alter_ids:
                retained.append(RetainedTablesData(ex_id, alter_id, score, alter_ids))
    print >> sys.stderr, 'Read {} retained tables'.format(len(retained))
    return retained

def escaped_TSV_to_HTML(x):
    # '\n' -> newline, '\p' -> pipe, '\\' -> backslash
    x = x.replace(r'\n', '\n').replace(r'\p', '|').replace('\\\\', '\\')
    # & -> '&amp;', '<' -> '&lt;', '>' -> '&gt;'
    x = x.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    # \n -> <br>
    x = x.replace('\n', '<br>')
    return x

def read_table(filename):
    with open(filename, 'r', 'utf8') as fin:
        lines = []
        for line in fin:
            cells = line.rstrip('\n').split('\t')
            cells = [escaped_TSV_to_HTML(x) for x in cells]
            lines.append(cells)
    return lines

def generate_json(example, metadata, table, data):
    sprite = set()
    for row in table:
        sprite.update(row)
    sprite = sorted(sprite)
    table_sprited = []
    for row in table:
        table_sprited.append([sprite.index(cell) for cell in row])
    stuff = {
        'id': example['id'],
        'question': example['utterance'],
        'metadata': {
            'title': metadata['title'],
            'url': metadata['url'],
            'tableIndex': metadata['tableIndex'],
            },
        'sprite': sprite,
        'table': table_sprited,
        'alterIndex': data.alter_id,
        'retained': {
            'score': data.score,
            'otherAlterIndices': data.other_alter_ids,
            },
        }
    return stuff

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-d', '--dataset_filename', default='t/data/training.tsv')
    parser.add_argument('-j', '--json_dir', default='t/json/')
    parser.add_argument('-o', '--out_dir', default='out/')
    parser.add_argument('-a', '--altered_dir', default='altered')
    parser.add_argument('-q', '--questions', default=8, type=int)
    parser.add_argument('retained_filename')
    args = parser.parse_args()

    if os.path.exists(args.out_dir):
        if raw_input('Delete {}? (y/N): '.format(args.out_dir))[0:].lower() != 'y':
            return
        shutil.rmtree(args.out_dir)
    os.makedirs(args.out_dir)

    # Read examples
    dataset = read_dataset(args.dataset_filename)

    # Read retained tables
    retained = read_retained(args.retained_filename)

    print >> sys.stderr, args.questions, 'per file'
    print >> sys.stderr, '->', len(retained) / args.questions, ' * ', args.questions,
    print >> sys.stderr, ' + ', len(retained) % args.questions
    fout = None
    for i, data in enumerate(retained):
        if i % 500 == 0:
            print >> sys.stderr, 'Processing {} / {} ...'.format(i, len(retained))
        if i % args.questions == 0:
            if fout is not None:
                fout.close()
            fout = open(os.path.join(args.out_dir, '{}.js'.format(i / args.questions)), 'w', 'utf8')
            fout.write('var QUESTIONS=[];\n')
        # Read table
        table = read_table(os.path.join(args.altered_dir, data.ex_id, str(data.alter_id) + '.tsv'))
        # Read example
        example = dataset[data.ex_id]
        xxx, yyy = re.match(r'csv/(\d+)-csv/(\d+).csv', example['context']).groups()
        # Read metadata
        with open(os.path.join(args.json_dir, xxx + '-json', yyy + '.json'), 'r', 'utf8') as fin:
            metadata = json.load(fin)
        # Generate JSON
        fout.write('QUESTIONS.push(')
        json.dump(generate_json(example, metadata, table, data), fout,
                ensure_ascii=False, separators=(',', ':'))
        fout.write(');\n')
    if fout is not None:
        fout.close()

if __name__ == '__main__':
    main()

