#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Download Wikipedia pages using the JSON dumped from get-wikipedia-pages.py

Each line in `json_file` is a JSON with fields `id` and `revision`.
The (old) revision of the Wikipedia page will be downloaded.

Outputs:
- [outdir]/[i].html = symlink to the downloaded page in web.cache
- [outdir]/[i].json = metadata in JSON format
"""

import sys, os, re, argparse, json, time
from codecs import open
from collections import defaultdict
from weblib.web import WebpageCache

BASEDIR = os.path.dirname(os.path.realpath(__file__))

def get_url(page_id, revision):
    url = 'http://en.wikipedia.org/wiki?action=render&curid=%d' % page_id
    if revision:
        url += '&oldid=%d' % revision
    return url

def download(data, i, outdir):
    url = get_url(data['id'], data['revision'])
    hashcode = data['hashcode'] = CACHE.get_hashcode(url)
    data['url'] = url
    result = CACHE.get_page(url)
    if result is not None and not os.path.exists(os.path.join(outdir, str(data['id']) + '.html')):
        os.symlink(os.path.join('..', 'web.cache', hashcode),
                   os.path.join(outdir, str(data['id']) + '.html'))
        with open(os.path.join(outdir, str(data['id']) + '.json'), 'w') as fout:
            json.dump(data, fout)
    if CACHE.cache_miss:
        print 'Sleep ...'
        time.sleep(1)

def main():
    global CACHE
    parser = argparse.ArgumentParser(description=__doc__,
            formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('-b', '--basedir', default='wikidump.cache')
    parser.add_argument('-o', '--outdir', default='output')
    parser.add_argument('json_file')
    args = parser.parse_args()

    CACHE = WebpageCache(basedir=args.basedir, timeout=50)
    outdir = os.path.join(args.basedir, args.outdir)

    if not os.path.isdir(outdir):
        os.mkdir(outdir)

    with open(args.json_file) as fin:
        for i, line in enumerate(fin):
            download(json.loads(line), i+1, outdir)

if __name__ == '__main__':
    main()
