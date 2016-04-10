#!/usr/bin/env python
# -*- coding: utf-8 -*-

import urllib, urllib2, urlparse, socket
import json, sys, os, hashlib, subprocess, time
from blacklist import BLACKLIST

BASEDIR = os.path.dirname(os.path.realpath(os.path.join(__file__, '..')))

class WebpageCache(object):
    def __init__(self, basedir=BASEDIR, dirname='web.cache', log=True, timeout=15):
        self.cachePath = os.path.join(basedir, dirname)
        if not os.path.exists(self.cachePath):
            os.mkdir(self.cachePath)
        self.log = log
        self.cache_miss = False
        self.timeout = timeout

    def get_hashcode(self, url):
        return hashlib.sha1(url).hexdigest()

    def get_path(self, url, already_hashed=False):
        if not already_hashed:
            url = self.get_hashcode(url)
        return os.path.join(self.cachePath, url)

    def get_current_datetime(self):
        return time.strftime("%Y-%m-%d-%H-%M-%S", time.gmtime())

    def open_in_browser(self, hashcode, browser="firefox"):
        path = os.path.join(self.cachePath, hashcode)
        subprocess.call([browser, path])

    def comment(self, url):
        return ' '.join(('<!--', urllib.quote(url),
                         self.get_current_datetime(), '-->\n'))

    def read(self, url, already_hashed=False):
        path = self.get_path(url, already_hashed)
        if os.path.exists(path):
            with open(path) as fin:
                error = False
                check_url = fin.readline().strip()
                if check_url == 'ERROR':
                    error = True
                    error_message = fin.readline().strip()
                    check_url = fin.readline()
                if not already_hashed:
                    tokens = check_url.split()
                    assert len(tokens) > 2 and tokens[1] == urllib.quote(url), path
                if error:
                    return WebLoadingError(error_message)
                else:
                    return fin.read()
        
    def write(self, url, content, already_hashed=False):
        path = self.get_path(url, already_hashed)
        with open(path, 'w') as fout:
            fout.write(self.comment(url))
            fout.write(content)

    def write_error(self, url, error, already_hashed=False):
        path = self.get_path(url, already_hashed)
        with open(path, 'w') as fout:
            fout.write('ERROR\n')
            fout.write(error.replace('\n', ' ') + '\n')
            fout.write(self.comment(url))

    def get_page(self, url, force=False, check_html=True):
        result = self.read(url)
        if result and not force:
            self.cache_miss = False
            if isinstance(result, WebLoadingError):
                if self.log:
                    print >> sys.stderr, '[ERROR]', result
                result = None
        else:
            self.cache_miss = True
            try:
                if self.log:
                    print >> sys.stderr, 'Downloading from', url, '...'
                # Check blacklist
                parsed_url = urlparse.urlparse(url)
                if parsed_url.netloc in BLACKLIST:
                    raise WebLoadingError('URL %s in blacklist' % url)
                # Open web page
                opener = urllib2.build_opener()
                opener.addheaders = [
                    ('User-agent', 
                     'Mozilla/5.0 (compatible; MSIE 7.0; Windows NT 6.0)')]
                response = opener.open(url, timeout=self.timeout)
                # Check content type to prevent non-HTML
                content_type = response.info().type
                if check_html and content_type != 'text/html':
                    raise WebLoadingError("Non-HTML response: %s" %
                                          content_type)
                result = response.read()
                self.write(url, result)
            except Exception, e:
                if self.log:
                    print >> sys.stderr, '[ERROR] ', e
                if isinstance(e, (WebLoadingError, urllib2.URLError, socket.error)):
                    self.write_error(url, str(e.message))
                result = None
        if self.log:
            if self.cache_miss:
                print >> sys.stderr, 'Retrieved "%s"' % url
            else:
                print >> sys.stderr, ('Loaded "%s" from cache (%s)' %
                                      (url, self.get_path(url)))
        return result

    ################################################################
    # GOOGLE SUGGEST

    GOOGLE_SUGGEST_URL = 'http://suggestqueries.google.com/complete/search?client=firefox&q='

    def get_google_suggest_url(self, before, after=''):
        answer = self.GOOGLE_SUGGEST_URL + urllib.quote(before) + urllib.quote(after)
        if after:
            answer += '&cp=' + str(len(before))
        return answer

    def get_from_google_suggest(self, before, after=''):
        url = self.get_google_suggest_url(before, after)
        return json.loads(self.get_page(url, check_html=False))[1]

    ################################################################
    # GOOGLE SEARCH -- old API
    # The important fields of each result are
    # - url (+ unescapedUrl, visibleUrl, cacheUrl)
    # - titleNoFormatting (+ title)
    # - content
    
    GOOGLE_SEARCH_URL = 'http://ajax.googleapis.com/ajax/services/search/web?v=1.0&rsz=large&q='

    def get_google_search_url(self, keyword):
        answer = self.GOOGLE_SEARCH_URL + urllib.quote(keyword)
        return answer

    def get_from_google_search(self, keyword, raw=False):
        url = self.get_google_search_url(keyword)
        result = self.get_page(url, check_html=False)
        if raw:
            return result
        return json.loads(result)

    def get_urls_from_google_search(self, keyword):
        results = self.get_from_google_search(keyword)['responseData']['results']
        return [(x['unescapedUrl'], x['titleNoFormatting']) for x in results]

    GOOGLE_PAUSE = 30

    def get_from_google_search_with_backoff(self, keyword):
        url = self.get_google_search_url(keyword)
        result = self.get_page(url, check_html=False)
        while True:
            try:
                return json.loads(result)['responseData']['results']
            except:
                # Google nailed me! Exponential backoff!
                print >> sys.stderr, ('Hide from Google for %d seconds ...' % 
                                      WebpageCache.GOOGLE_PAUSE)
                time.sleep(WebpageCache.GOOGLE_PAUSE)
                WebpageCache.GOOGLE_PAUSE *= 2
            result = self.get_page(url, check_html=False, force=True)

    def get_urls_from_google_search_with_backoff(self, keyword):
        results = self.get_from_google_search_with_backoff(keyword)
        return [(x['unescapedUrl'], x['titleNoFormatting']) for x in results]

    ################################################################
    # GOOGLE SEARCH -- Custom Search

    CUSTOM_GOOGLE_SEARCH_URL = 'https://www.googleapis.com/customsearch/'\
        'v1?key=%s&cx=%s&alt=json&safe=high&q=%s'

    def set_google_custom_search_keys(self, api_key, cx):
        self.api_key = api_key
        self.cx = cx

    def get_google_custom_search_url(self, keyword):
        answer = self.CUSTOM_GOOGLE_SEARCH_URL % \
            (self.api_key, self.cx, urllib.quote(keyword))
        return answer

    def get_from_google_custom_search(self, keyword, raw=False):
        url = self.get_google_custom_search_url(keyword)
        answer = self.get_page(url, check_html=False)
        if raw:
            return answer
        return json.loads(answer)

    def get_urls_from_google_custom_search(self, keyword):
        results = self.get_from_google_custom_search(keyword)['items']
        return [(x['link'], x.get('title', '')) for x in results]

    def get_urls_from_google_hybrid_search(self, keyword):
        '''Return (cache_path, results)'''
        old_url = self.get_google_search_url(keyword)
        result = self.read(old_url)
        if result and not isinstance(result, WebLoadingError):
            # Found result in cache
            try:
                results = json.loads(result)['responseData']['results']
                return (self.get_path(old_url),
                        [(x['unescapedUrl'], x['titleNoFormatting'])
                        for x in results])
            except:
                # Stale bad cache ...
                pass
        # Use Custom search
        return (self.get_path(self.get_google_custom_search_url(keyword)),
                self.get_urls_from_google_custom_search(keyword))

class WebLoadingError(Exception):
    def __init__(self, msg):
        self.args = (msg,)
        self.msg = msg
        self.message = msg
