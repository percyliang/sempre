#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""LispTree utilities."""

import sys, os, shutil, re, argparse, json
from codecs import open
from itertools import izip
from collections import defaultdict

def parse(x):
    """Parse a LispTree string into a (nested) Python list."""
    stack = [[]]
    buff = []
    in_quote = False
    is_escape = False
    for c in x:
        if in_quote:
            if is_escape:
                is_escape = False
                buff.append(c)
            else:
                if c == '"':
                    in_quote = False
                    stack[-1].append(''.join(buff))
                    buff = None
                elif c == '\\':
                    is_escape = True
                else:
                    buff.append(c)
        else:
            if c == '(':
                assert buff == []
                stack.append([])
            elif c == '"':
                assert buff == []
                in_quote = True
            elif c == ')':
                if buff:
                    stack[-1].append(''.join(buff))
                buff = []
                last = stack.pop()
                stack[-1].append(last)
            elif c in ' \n\t':
                if buff:
                    stack[-1].append(''.join(buff))
                buff = []
            else:
                assert buff is not None
                buff.append(c)
    assert len(stack) == 1 and len(stack[0]) == 1
    return stack[0][0]

if __name__ == '__main__':
    # Tests
    a = '(derivation (formula (max ((reverse fb:cell.cell.date) ((reverse fb:row.row.year) (and (fb:row.row.league fb:cell.usl_a_league) (fb:row.row.playoffs (!= fb:cell.usl_a_league))))))) (value (list (date 2004 -1 -1))) (type (union fb:type.number fb:type.datetime)) (canonicalUtterance $ROOT:8))'
    print a
    print parse(a)
    a = r'(derivation (formula ((reverse fb:row.row.venue) (argmax (number 1) (number 1) (fb:row.row.venue ((reverse fb:row.row.venue) (fb:row.row.position fb:cell.1st))) (reverse (lambda x ((reverse fb:row.row.index) (var x))))))) (value (list (name fb:cell.bangkok_thailand "Bangkok, \"Thai\"land"))) (type fb:type.cell) (canonicalUtterance $ROOT:8))'
    print a
    print parse(a)
