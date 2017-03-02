#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys
from codecs import open

# http://stackoverflow.com/a/616686
class TeeOut(object):
    def __init__(self, filename, mode='w'):
        import sys
        self.file = open(filename, mode, 'utf8')
        self.stdout = sys.stdout
        sys.stdout = self
    def __del__(self):
        import sys
        sys.stdout = self.stdout
        self.file.close()
    def write(self, data):
        self.file.write(data)
        self.file.flush()
        self.stdout.write(data)
        self.stdout.flush()
        
class TeeErr(object):
    def __init__(self, filename, mode='w'):
        import sys
        self.file = open(filename, mode, 'utf8')
        self.stderr = sys.stderr
        sys.stderr = self
    def __del__(self):
        import sys
        sys.stderr = self.stderr
        self.file.close()
    def write(self, data):
        self.file.write(data)
        self.file.flush()
        self.stderr.write(data)
        self.stderr.flush()

if __name__ == '__main__':
    pass
