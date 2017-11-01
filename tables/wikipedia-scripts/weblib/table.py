#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Table processor.
Get statistics about a table and convert it to CSV.
"""

import sys, os, re, json
from codecs import open
from collections import defaultdict
from bs4 import BeautifulSoup as BeautifulSoupOriginal
def BeautifulSoup(markup=""):
    return BeautifulSoupOriginal(markup, 'html.parser')

class Table(object):
    NORM_NONE = 0
    NORM_CORNER = 1
    NORM_DUPLICATE = 2
    SOUP = BeautifulSoup()

    def __init__(self, table, normalization=0, remove_hidden=True):
        """Create table from a BeautifulSoup table Tag."""
        assert table.name == 'table'
        self.table = table
        if remove_hidden:
            self.remove_hidden()
        if normalization == Table.NORM_CORNER:
            self.normalize_table()
        elif normalization == Table.NORM_DUPLICATE:
            self.normalize_table(deep=True)
        self.get_cells()

    @staticmethod
    def get_wikitable(raw_html, index=None, **kwargs):
        soup = BeautifulSoup(raw_html)
        tables = soup.find_all('table', class_='wikitable')
        if index is None:
            return [Table(x, **kwargs) for x in tables]
        else:
            return Table(tables[index], **kwargs)

    def check_hidden(self, tag):
        classes = tag.get('class', [])
        if 'reference' in classes or 'sortkey' in classes:
            return True
        if 'display:none' in tag.get('style', ''):
            return True
        return False

    def remove_hidden(self):
        """Remove hidden elements."""
        for tag in self.table.find_all(self.check_hidden):
            tag.extract()

    def get_cells(self):
        """Each cell is (tag, text)"""
        self.rows, self.cells = [], []
        for x in self.table.find_all('tr', recursive=False):
            row = []
            for y in x.find_all(['th', 'td'], recursive=False):
                row.append((y.name, y.text.strip()))
            self.rows.append(row)
            self.cells.extend(row)
        self.num_rows = len(self.rows)
        self.num_cols = 0 if not self.num_rows else max(len(row) for row in self.rows)
        self.num_cells = len(self.cells)
        self.cols = [[] for i in xrange(self.num_cols)]
        for row in self.rows:
            for i, cell in enumerate(row):
                self.cols[i].append(cell)
    
    ################ Table normalization ################

    def get_int(self, cell, key):
        try:
            return int(cell.get(key, 1))
        except ValueError:
            try:
                return int(re.search('[0-9]+', cell[key]).group())
            except:
                return 1

    def get_cloned_cell(self, cell, rowspan=1, deep=False):
        if deep:
            # Hacky but works
            return BeautifulSoup(unicode(cell)).contents[0]
        tag = Table.SOUP.new_tag(cell.name)
        if rowspan > 1:
            tag['rowspan'] = rowspan
        return tag

    def normalize_table(self, deep=False):
        """Fix the table in-place."""
        # Fix colspan
        num_cols = 0
        for tr in self.table.find_all('tr', recursive=False):
            for cell in tr.find_all(['th', 'td'], recursive=False):
                colspan = self.get_int(cell, 'colspan')
                rowspan = self.get_int(cell, 'rowspan')
                if colspan <= 1:
                    continue
                cell['old-colspan'] = cell['colspan']
                del cell['colspan']
                for i in xrange(2, colspan + 1):
                    cell.insert_after(self.get_cloned_cell(cell, rowspan=rowspan, deep=deep))
            num_cols = max(num_cols, len(tr.find_all(['th', 'td'], recursive=False)))
        # Fix rowspan
        counts = defaultdict(int)
        spanned_cells = dict()
        for tr in self.table.find_all('tr', recursive=False):
            cell = None
            cells = tr.find_all(['th', 'td'], recursive=False)
            k = 0
            for i in xrange(num_cols):
                if counts[i] > 0:
                    # Create a new element caused by rowspan
                    new_cell = self.get_cloned_cell(spanned_cells[i], deep=deep)
                    if not cell:
                        tr.insert(0, new_cell)
                    else:
                        cell.insert_after(new_cell)
                    cell = new_cell
                    counts[i] -= 1
                else:
                    if k >= len(cells):    # Unfilled row
                        continue
                    cell = cells[k]
                    k += 1
                    rowspan = self.get_int(cell, 'rowspan')
                    if rowspan <= 1:
                        continue
                    counts[i] = rowspan - 1
                    spanned_cells[i] = cell
                    cell['old-rowspan'] = cell['rowspan']
                    del cell['rowspan']

def test():
    text = """
    <table>
      <tr><th>1</th><th colspan=2>2</th></tr>
      <tr><th rowspan=2 colspan=2><a href="http://www.example.com">3</a></th>
          <td colspan=2 class=yay>4</td></tr>
      <tr><td>5</td><td>6</td></tr>
      <tr><td colspan=3 rowspan=1>7</td></tr>
      <tr><th rowspan=3><table><tr><td>8</td><td>9</td></tr></table><br/>10</th></tr>
    </table>
    """
    table = Table(BeautifulSoup(text).table)
    print table.table
    table = Table(BeautifulSoup(text).table, normalization=Table.NORM_CORNER)
    print table.table
    table = Table(BeautifulSoup(text).table, normalization=Table.NORM_DUPLICATE)
    print table.table

if __name__ == '__main__':
    test_wiki()
