#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Get predictions from the log file of SEMPRE."""

import sys, os, shutil, re, argparse

PATTERN = re.compile(r'Pred@0000: '
        r'\(derivation \(formula (.*)\)\) '
        r'\(value (.*)\) '
        r'\(type (.*)\)\) \[score=(.*), prob=(.*), comp=(.*)\]')

def lisptree_to_python_object(charbuffer):
    """Convert the lisptree to Python object.

    Args:
        charbuffer: REVERSED list of characters of the lisptree string.
        Characters will be consumed from the list.
    """
    c = charbuffer.pop()
    if c == '(':
        answer = []
        while charbuffer[-1] != ')':
            if charbuffer[-1] == ' ':
                charbuffer.pop()
            else:
                answer.append(lisptree_to_python_object(charbuffer))
        assert charbuffer.pop() == ')'
        return answer
    elif c == '"':
        answer = []
        while charbuffer[-1] != '"':
            c = charbuffer.pop()
            if c == '\\':
                answer.append(charbuffer.pop())
            else:
                answer.append(c)
        assert charbuffer.pop() == '"'
        return ''.join(answer)
    else:
        answer = [c if c != '\\' else charbuffer.pop()]
        while charbuffer[-1] not in (' ', ')'):
            c = charbuffer.pop()
            if c == '\\':
                answer.append(charbuffer.pop())
            else:
                assert c != '('
                answer.append(c)
        return ''.join(answer)

def lisptree_to_values(tree):
    assert tree.startswith('(list ') and tree.endswith(')')
    tree = lisptree_to_python_object(list(tree.decode('utf8'))[::-1])
    assert tree[0] == 'list'
    answer = []
    for subtree in tree[1:]:
        if subtree[0] == 'number':
            answer.append(float(subtree[1]))
        elif subtree[0] == 'date':
            answer.append('{}-{}-{}'.format(
                int(subtree[1]) if subtree[1] != '-1' else 'xx',
                int(subtree[2]) if subtree[2] != '-1' else 'xx',
                int(subtree[3]) if subtree[3] != '-1' else 'xx'))
        else:
            assert subtree[0] == 'name'
            answer.append(re.sub('\s+', ' ', subtree[2]).strip())
    return '\t'.join(unicode(x) for x in answer)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('infile', help='log file')
    parser.add_argument('iteration', help='iteration to extract')
    args = parser.parse_args()

    prefix = 'iter=%s:' % args.iteration
    ex_id = None
    with open(args.infile) as fin:
        for line in fin:
            line = line.strip()
            if line.startswith(prefix):
                if ex_id is not None:
                    # No prediction for the previous example
                    print ex_id
                ex_id = line.split()[3]
            elif ex_id is not None and line.startswith('Pred@0000:'):
                match = PATTERN.match(line)
                formula, denotation, deno_type, score, prob, comp = match.groups()
                denotation = lisptree_to_values(denotation)
                print u'{}\t{}'.format(ex_id, denotation)
                ex_id = None
    if ex_id is not None:
        print '\t'.join([ex_id, 'None'])

if __name__ == '__main__':
    main()

