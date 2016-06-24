#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""DEPRECATED. Use fictitious-experiments.py instead."""

import sys, os, shutil, re, argparse, json, gzip
from codecs import open
from itertools import izip
from collections import defaultdict, Counter

def clean(x):
    # Remove actual string from names
    x = x.decode('utf8').replace(ur'\"', '')
    x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*)\)', r'(name \1)', x)
    x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*) [^")]*\)', r'(name \1)', x)
    x = re.sub(ur'\(name fb:(?:cell|part)\.([^ )]*) "[^"]*"\)', r'(name \1)', x)
    return x

def read_denotation_file(ex_id, fin):
    """Return n, k, d, denotations, grid"""
    n, k, d = [int(x) for x in fin.readline().split()]
    denotations = []
    for i in xrange(d):
        denotations.append(clean(fin.readline()[:-1]))
    if not denotations:
        print >> sys.stderr, 'Warning: no denotations - ', ex_id
    elif denotations[0] == '(list)':
        print >> sys.stderr, 'Warning: denotations[0] == (list) - ', ex_id
    denotations.append(None)
    grid = []
    for i in xrange(n):
        row = [denotations[int(x)] for x in fin.readline()[:-1].split()]
        grid.append(row)
    denotations.pop()
    return n, k, d, denotations, grid

################################################

# Count number of equivalence classes
def process1(ex_id, fin):
    n, k, d, denotations, grid = read_denotation_file(ex_id, fin)
    equiv_classes = {0: set(), 5: set(), 10: set(),
            30: set(), 100: set(), 300: set()}
    for row in grid:
        row = tuple(row)
        for num_tables, value in equiv_classes.items():
            value.add(row[:num_tables+1])
    
    fields = [ex_id, n, k, d]
    for key, value in sorted(equiv_classes.items()):
        fields.append(len(value))
    print '\t'.join(str(x) for x in fields)
    sys.stdout.flush()

# Find out how many are split from main class when going from 30 --> 300
def process2(ex_id, fin):
    n, k, d, denotations, grid = read_denotation_file(ex_id, fin)
    thirties = defaultdict(Counter)
    for row in grid:
        row = tuple(row)
        thirties[row[:31]][row[:301]] += 1
        #thirties[row[:6]][row[:31]] += 1

    cores = splits = 0
    for key, value in thirties.items():
        core = value.most_common(1)[0][1]
        cores += core
        splits += (sum(value.values()) - core)
    
    fields = [ex_id, n, k, d, len(thirties), cores, splits]
    print '\t'.join(str(x) for x in fields)
    sys.stdout.flush()

# Find out the splitters from 30 --> 300
def process3(ex_id, fin):
    n, k, d, denotations, grid = read_denotation_file(ex_id, fin)
    thirties = defaultdict(lambda: defaultdict(list))
    counts = Counter()
    for i, row in enumerate(grid):
        row = tuple(row)
        thirties[row[:31]][row].append(i)
        counts[row[:31]] += 1

    cores = []
    splits = []
    if counts:
        first_thirty_one, count = counts.most_common(1)[0]
        full = thirties[first_thirty_one]
        # full = map from full denotation tuple to deriv indices
        core = max(len(x) for x in full.values())
        for indices in full.values():
            if len(indices) == core:
                cores.extend(indices)
            else:
                splits.extend(indices)
    if not splits:
        cores = []
    else:
        splits = sorted(splits)
    
    fields = [ex_id, n, k, d, len(thirties), len(cores), len(splits),
            ' '.join(str(x) for x in cores[:30]),
            ' '.join(str(x) for x in splits[:30])]
    print '\t'.join(str(x) for x in fields)
    sys.stdout.flush()

# Find out more stuff about annotation
def process5(ex_id, fin, ann_fin, ret_data, size_path, formula_path):
    n, k, d, denotations, grid = read_denotation_file(ex_id, fin)
    grid = [row[:31] for row in grid]

    # Equiv class to list of indices
    equiv_classes = defaultdict(list)
    for i, row in enumerate(grid):
        equiv_classes[tuple(row)].append(i)

    # Read annotation
    annotations = []
    for line in ann_fin:
        annotations.append(clean(line[:-1]))
    annotations = tuple(annotations[:31])

    # Read size
    if size_path:
        with open(size_path) as fin:
            sizes = [int(x) for x in fin]
    else:
        sizes = [0] * n
    sizes_counter = Counter(sizes)

    # Read formulas
    if formula_path:
        with (open if formula_path.endswith('.gz') else gzip.open)(formula_path) as fin:
            formulas = fin.readlines()
    else:
        formulas = [''] * n
    
    score, chosen_entropy = ret_data
    chosen_random = range(len(chosen_entropy))

    fields = [ex_id, n, k, d, len(equiv_classes),
            '{:.3}'.format(score),
            ' '.join(str(x) for x in chosen_entropy),
            ' '.join('{}:{}'.format(u, sizes_counter[u])
                for u in xrange(1, 9))]
    if annotations[0] == 'null':
        fields.append('no')
    else:
        fields.append('yes')
        # Count the number of matches
        matched = len(equiv_classes[annotations])
        fields.append(matched)
        # Chosen equiv classes based on chosen tables
        def count_on_chosen(chosen):
            chosen_annotations = tuple(annotations[x] for x in chosen)
            num_classes_matched = 0
            num_derivs_matched = []
            derivs_matched = []
            for key, value in equiv_classes.items():
                chosen_key = tuple(key[x] for x in chosen)
                if chosen_key == chosen_annotations:
                    num_classes_matched += 1
                    num_derivs_matched.append(len(value))
                    derivs_matched.extend(value)
            fields.extend([num_classes_matched, sum(num_derivs_matched)])
            fields.append(list(reversed(sorted(num_derivs_matched))))
            derivs_matched.sort()
            matched_sizes = [sizes[x] for x in derivs_matched]
            matched_sizes_counter = Counter(matched_sizes)
            fields.append(' '.join('{}:{}'.format(u, matched_sizes_counter[u])
                for u in xrange(1, 9)))
            # Is the formula with the lowest size the annotated one?
            if matched_sizes:
                min_size, min_index = min((u,i) for (u,i)
                        in zip(matched_sizes, derivs_matched))
                fields.append([min_size, min_index, formulas[min_index]])
            else:
                fields.append('')
        count_on_chosen(chosen_entropy)
        count_on_chosen(chosen_random)

    print '\t'.join(str(x) for x in fields)
    sys.stdout.flush()

# Find out stuff about annotation; turked version
def process6(ex_id, fin, ann_fin, size_path, formula_path, exec_dir):
    # These are only for computing equivalence classes
    n, k, d, denotations, grid = read_denotation_file(ex_id, fin)
    grid = [row[:31] for row in grid]

    # Equiv class to list of indices
    equiv_classes = defaultdict(list)
    for i, row in enumerate(grid):
        equiv_classes[tuple(row)].append(i)
    deriv_to_rep = {}
    for equiv_class in equiv_classes.values():
        rep = min(equiv_class)
        for x in equiv_class:
            deriv_to_rep[x] = rep

    # Read annotation
    annotations = []
    for line in ann_fin:
        annotations.append(clean(line[:-1]))
    annotations = tuple(annotations[:31])

    # Read size
    if size_path:
        with open(size_path) as fin:
            sizes = [int(x) for x in fin]
    else:
        sizes = [0] * n
    sizes_counter = Counter(sizes)

    # Read formulas
    if formula_path:
        with (open if formula_path.endswith('.gz') else gzip.open)(formula_path) as fin:
            formulas = fin.readlines()
    else:
        formulas = [''] * n

    fields = [ex_id, n, k, d, len(equiv_classes),
            ' '.join('{}:{}'.format(u, sizes_counter[u])
                for u in xrange(1, 9))]
    correct_class = None
    if annotations[0] == 'null':
        fields.append('no')
        fields.append('')
    else:
        fields.append('yes')
        # Count the number of matches
        matched = len(equiv_classes[annotations])
        fields.append(matched)
        if matched > 0:
            correct_class = min(equiv_classes[annotations])
    fields.append(correct_class)

    def get_flag(x):
        if x == '(number 42)' or x == '(name Ao)':
            return 'G'
        elif x == '(number 13)' or x == '(name Ax)':
            return 'F'
        elif x == '(number 666)':
            return 'B'
        elif x == '(number 777)' or x == '(name Bo)':
            return 'g'
        elif x == '(number 999)' or x == '(name Bx)':
            return 'f'
        elif x == '(number 88)' or x == '(name X)':
            return 'X'
        else:
            return None
        
    # Read Turk data
    if os.path.exists(os.path.join(exec_dir, 'denotations')):
        prefix = ''
    else:
        prefix = 'actual-'
    with gzip.open(os.path.join(exec_dir, prefix + 'denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        _, _, _, _, turk_grid = read_denotation_file(ex_id, fin)
    with gzip.open(os.path.join(exec_dir, 'check-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        _, _, _, _, check_grid = read_denotation_file(ex_id, fin)
        check_grid = [[get_flag(x) for x in y] for y in check_grid]
    with gzip.open(os.path.join(exec_dir, prefix + 'annotated-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        anno_grid = [x[:-1] for x in fin]
    with gzip.open(os.path.join(exec_dir, 'check-annotated-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        check_anno_grid = [x[:-1] for x in fin]
        check_anno_grid = [get_flag(x) for x in check_anno_grid]
    
    # Used indices
    if check_grid:
        used_indices = [i for (i,x) in enumerate(check_grid[0]) if x is not None]
        flags = [check_grid[0][i] for i in used_indices]
    else:
        used_indices = [i for (i,x) in enumerate(anno_grid) if x is not None]
        flags = [check_anno_grid[i] for i in used_indices]
    fields.append(used_indices)
    fields.append(''.join([{'G': 'A', 'F': 'A', 'B': 'B',
        'g': 'B', 'f': 'B', 'X': 'X', None: '?'}[x] for x in flags]))
    fields.append(check_anno_grid[0])

    # Scheme is a function that takes in a check vector (only for used indices)
    #   and return True (matched) or False (not matched).
    #
    # For each possible scheme, compute
    # - number of classes matched
    # - number of formulas matched
    # - class sizes
    # - whether the annotated formula matches turked data
    # - whether the "correct" class is among the matched classes
    def check_scheme(scheme):
        hello = []
        for check_vector in check_grid:
            hello.append(scheme([check_vector[x] for x in used_indices]))
        hello_anno = scheme([check_anno_grid[x] for x in used_indices])
        # num classes matched
        matched_derivs = [i for (i,x) in enumerate(hello) if x]
        matched_classes = set(deriv_to_rep[x] for x in matched_derivs)
        fields.append(len(matched_classes))
        fields.append(len(matched_derivs))
        class_sizes = [(len(equiv_classes[tuple(grid[x])]), x) for x in matched_classes]
        #class_sizes.sort(key=lambda x: -x[0])
        #fields.append([x[1] for x in class_sizes])
        #fields.append([x[0] for x in class_sizes])
        # manually annotated formula
        fields.append(hello_anno)
        # correct is among?
        fields.append(correct_class in matched_classes)

    def no_big_f(vector):
        return all(x != 'F' for x in vector)
    check_scheme(no_big_f)
    def no_f_ever(vector):
        return all((x != 'f' and x != 'F') for x in vector)
    check_scheme(no_f_ever)
    def at_most_one_big_f(vector):
        return sum([x == 'F' for x in vector], 0) <= 1
    check_scheme(at_most_one_big_f)

    print '\t'.join(str(x) for x in fields)
    sys.stdout.flush()

# Dump equivalence classes to a file
def process7(ex_id, fin, ann_fin, size_path, formula_path, exec_dir, outdir):
    n, k, d, denotations, grid = read_denotation_file(ex_id, fin)
    denotations_set = set(denotations)
    #grid = [row[:31] for row in grid]

    # Read annotation
    annotations = []
    for line in ann_fin:
        annotations.append(clean(line[:-1]))
    for x in set(annotations):
        if x not in denotations_set and n > 0:
            print >> sys.stderr, 'WTF:', ex_id, n, k, d, x
    annotations = tuple(annotations)
    print annotations
    #annotations = annotations[:31]

    # Read size
    if size_path:
        with open(size_path) as fin:
            sizes = [int(x) for x in fin]
    else:
        sizes = [0] * n
    sizes_counter = Counter(sizes)

    # Read formulas
    if formula_path:
        with (open if formula_path.endswith('.gz') else gzip.open)(formula_path) as fin:
            formulas = [x.strip() for x in fin.readlines()]
    else:
        formulas = [''] * n

    fields = [ex_id, n, k, d]
    if annotations[0] == 'null':
        fields.append('no')
    else:
        fields.append('yes')

    def get_flag(x):
        if x == '(name Ao)':
            return 'G'
        elif x == '(name Ax)':
            return 'F'
        elif x == '(name Bo)':
            return 'g'
        elif x == '(name Bx)':
            return 'f'
        elif x == '(name X)':
            return 'X'
        else:
            assert x is None or x == 'null'
            return None
        
    # Read Turk data
    with gzip.open(os.path.join(exec_dir, 'actual-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        _, _, _, _, turk_grid = read_denotation_file(ex_id, fin)
    with gzip.open(os.path.join(exec_dir, 'check-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        _, _, _, _, check_grid = read_denotation_file(ex_id, fin)
        check_grid = [[get_flag(x) for x in y] for y in check_grid]
    with gzip.open(os.path.join(exec_dir, 'actual-annotated-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        anno_grid = [x[:-1] for x in fin]
    with gzip.open(os.path.join(exec_dir, 'check-annotated-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        check_anno_grid = [x[:-1] for x in fin]
        check_anno_grid = [get_flag(x) for x in check_anno_grid]

    # Dump equivalence classes
    # Also dump whether the class
    # - Matches the annotated formula
    # - Matches the turked data
    deno_tuple_to_indices = defaultdict(list)
    for i, row in enumerate(grid):
        row = tuple(row)
        deno_tuple_to_indices[row].append(i)
    classes = []
    flag_counts_classes = [0, 0, 0, 0]
    flag_counts_formulas = [0, 0, 0, 0]
    for deno_tuple, indices in deno_tuple_to_indices.iteritems():
        real = all(x != 'F' for x in check_grid[indices[0]])
        ideal = (deno_tuple == annotations)
        classes.append([real, ideal, indices])
        flag_counts_classes[real * 2 + ideal] += 1
        flag_counts_formulas[real * 2 + ideal] += len(indices)
    classes.sort(key=lambda x: (-x[0]-x[1], -x[0], -len(x[2])))
    fields.append(len(classes))
    fields.extend(flag_counts_classes)
    fields.extend(flag_counts_formulas)
    with open(os.path.join(outdir, 'nt-' + str(ex_id)), 'w') as fout:
        print >> fout, '{} classes ({} formulas) {{'.format(len(classes), n)
        for i, (real, ideal, indices) in enumerate(classes):
            equiv_class = [(sizes[i], formulas[i]) for i in indices]
            equiv_class.sort(key=lambda x: x[1])
            print >> fout, '  CLASS {} ({} formulas){}{} {{'.format(
                i, len(equiv_class), '[IDEAL]' if ideal else '',
                '[TURK]' if real else '')
            for s, f in equiv_class:
                print >> fout, '   ', s, f
            print >> fout, '  }'
        print >> fout, '}'

    print '\t'.join(str(x) for x in fields)
    sys.stdout.flush()

# Dump matching formulas to a file
def process8(ex_id, fin, formula_path, exec_dir, outdir):
    # Actually fin is not needed ...
    n, k, d = [int(x) for x in fin.readline().split()]
    fields = [ex_id, n, k, d]

    def get_flag(x):
        if x == '(name Ao)':
            return 'G'
        elif x == '(name Ax)':
            return 'F'
        elif x == '(name Bo)':
            return 'g'
        elif x == '(name Bx)':
            return 'f'
        elif x == '(name X)':
            return 'X'
        else:
            assert x is None or x == 'null'
            return None
        
    # Read Turk data
    with gzip.open(os.path.join(exec_dir, 'check-denotations', 'nt-{}.gz'.format(ex_id))) as fin:
        _, _, _, _, check_grid = read_denotation_file(ex_id, fin)
        check_grid = [[get_flag(x) for x in y] for y in check_grid]
    if n > 0:
        used_indices = [i for (i,x) in enumerate(check_grid[0]) if x is not None]
        flags = [check_grid[0][i] for i in used_indices]
    else:
        used_indices = []
        flags = []
    fields.append(''.join([{'G': 'A', 'F': 'A', 'B': 'B',
        'g': 'B', 'f': 'B', 'X': 'X', None: '?'}[x] for x in flags]))

    # Get the "correct" derivations
    corrects = []
    cur_i = 0
    with gzip.open(formula_path) as fin:
        for line in fin:
            line = line.strip()
            if line.startswith('(derivation (formula'):
                if all(check_grid[cur_i][i] != 'F' for i in used_indices):
                    corrects.append(line)
                cur_i += 1
    fields.append(len(corrects))

    # Dump matching formulas
    with gzip.open(os.path.join(outdir, 'nt-{}.gz'.format(ex_id)), 'w') as fout:
        for correct in corrects:
            print >> fout, correct

    print '\t'.join(str(x) for x in fields)
    sys.stdout.flush()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-s', '--step', type=int, default=1)
    parser.add_argument('-a', '--annotation-dir',
            help='directory to search for dumped annotated formula denotations')
    parser.add_argument('-r', '--retained-tables',
            help='path to retained-tables file')
    parser.add_argument('-z', '--sizes-dir',
            help='path to sizes directory')
    parser.add_argument('-f', '--formulas-dir',
            help='path to formulas directory')
    parser.add_argument('-e', '--exec-dir',
            help='path to exec dir with denotations, annotated-denotations, check-*')
    parser.add_argument('-R', '--range', nargs=2, type=int,
            help='range of example ids')
    parser.add_argument('denotation_dir',
            help='directory to search for dumped denotations')
    parser.add_argument('-o', '--outdir',
            help='output directory')
    args = parser.parse_args()
    
    # Read denotation directories
    ex_id_to_path = {}
    directory = args.denotation_dir
    for filename in os.listdir(directory):
        match = re.match(r'nt-(\d+)\.gz', filename)
        assert match, filename
        ex_id_to_path[int(match.group(1))] = os.path.join(directory, filename)
    print >> sys.stderr, 'There are', len(ex_id_to_path), 'denotation files'

    ex_id_to_ann_path = {}
    if args.annotation_dir:
        directory = args.annotation_dir
        for filename in os.listdir(directory):
            match = re.match(r'nt-(\d+)\.gz', filename)
            assert match, filename
            ex_id_to_ann_path[int(match.group(1))] = os.path.join(directory, filename)
    print >> sys.stderr, 'There are', len(ex_id_to_ann_path), 'annotated denotation files'

    ex_id_to_size_path = {}
    if args.sizes_dir:
        directory = args.sizes_dir
        for filename in os.listdir(directory):
            match = re.match(r'nt-(\d+)', filename)
            assert match, filename
            ex_id_to_size_path[int(match.group(1))] = os.path.join(directory, filename)
    print >> sys.stderr, 'There are', len(ex_id_to_size_path), 'annotated sizes files'

    ex_id_to_formula_path = {}
    if args.formulas_dir:
        directory = args.formulas_dir
        for filename in os.listdir(directory):
            match = re.match(r'nt-(\d+)(\.gz)?', filename)
            assert match, filename
            ex_id_to_formula_path[int(match.group(1))] = os.path.join(directory, filename)
    print >> sys.stderr, 'There are', len(ex_id_to_formula_path), 'annotated formulas files'

    if args.retained_tables:
        ret_data = {}
        with open(args.retained_tables) as fin:
            for line in fin:
                line = line[:-1].split('\t')
                ex_id = int(line[0][3:])
                score = float(line[1])
                chosen = [int(x) for x in line[2].split()]
                ret_data[ex_id] = (score, chosen)

    print >> sys.stderr, 'STEP:', args.step
    for i, ex_id in enumerate(sorted(ex_id_to_path)):
        if i % 20 == 0:
            print >> sys.stderr, 'Processing', i
        if args.range and not (args.range[0] <= i <= args.range[1]):
            continue
        with gzip.open(ex_id_to_path[ex_id]) as fin:
            if args.step == 1:
                process1(i, fin)
            elif args.step == 2:
                process2(i, fin)
            elif args.step == 3:
                process3(i, fin)
            elif args.step == 5:
                with gzip.open(ex_id_to_ann_path[ex_id]) as ann_fin:
                    process5(i, fin, ann_fin, ret_data[ex_id],
                            ex_id_to_size_path.get(i),
                            ex_id_to_formula_path.get(i))
            elif args.step == 6:
                with gzip.open(ex_id_to_ann_path[ex_id]) as ann_fin:
                    process6(i, fin, ann_fin,
                            ex_id_to_size_path.get(i),
                            ex_id_to_formula_path.get(i),
                            args.exec_dir)
            elif args.step == 7:
                assert args.outdir
                if not os.path.exists(args.outdir):
                    os.makedirs(args.outdir)
                with gzip.open(ex_id_to_ann_path[ex_id]) as ann_fin:
                    process7(i, fin, ann_fin,
                            ex_id_to_size_path.get(i),
                            ex_id_to_formula_path.get(i),
                            args.exec_dir, args.outdir)
            elif args.step == 8:
                assert args.outdir
                if not os.path.exists(args.outdir):
                    os.makedirs(args.outdir)
                process8(i, fin,
                        ex_id_to_formula_path.get(i),
                        args.exec_dir, args.outdir)
    print >> sys.stderr, 'DONE!'

if __name__ == '__main__':
    main()

