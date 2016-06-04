#!/usr/bin/env python
# -*- coding: utf-8 -*-

import re, sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'external'))

from bs4 import BeautifulSoup, element
import tidylib
tidylib.BASE_OPTIONS = {
    'output-html': 1,
    'indent': 0,
    'tidy-mark': 0,
    'wrap': 0,
    'doctype': 'strict',
    'force-output': 1,
}

WHITELIST_NAMES = set(
    ('html', 'head', 'meta', 'title', #'noscript',
     'body', 'section', 'nav', 'article', 'aside',
     'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
     'header', 'footer', 'address', 'main',
     'p', 'hr', 'pre', 'blockquote',
     'ol', 'ul', 'li', 'dl', 'dt', 'dd',
     'figure', 'figcaption', 'div',
     'a', 'em', 'strong', 'small', 's', 'cite', 'q', 'dfn',
     'abbr', 'data', 'time', 'code', 'var', 'samp', 'kbd',
     'sub', 'sup', 'i', 'b', 'u', 'mark', 'ruby', 'rt', 'rp',
     'wbr', 'ins', 'del', 'bdi', 'bdo', 'span', 'br',
     'img', 'table', 'caption', 'colgroup', 'col',
     'tbody', 'thead', 'tfoot', 'tr', 'td', 'th',
     'form', 'fieldset', 'legend', 'label', 'input', 'button',
     'select', 'datalist', 'optgroup', 'option', 'textarea',
     'keygen', 'output', 'progress', 'meter',
     'details', 'summary', 'menuitem', 'menu',
     'acronym', 'basefont', 'big', 'blink', 'center',
     'font', 'marquee', 'nobr', 'noframes', 'strike', 'tt')
    )

WHITELIST_ATTRS = set(
    ('colspan', 'rowspan')
    )

WHITELIST_NAME_ATTRS = set(
    (('meta', 'charset'), ('img', 'alt'), ('img', 'title'))
    )

INPUT_TYPES = set(
    ('checkbox', 'color', 'date', 'datetime', 'datetime-local',
     'email', 'hidden', 'month', 'number',
     'password', 'radio', 'range', 'tel', 'text', 'time', 'url', 'week')
    )

INPUT_BUTTON_TYPES = set(
    ('button', 'reset', 'submit', 'file', 'image')
    )

def is_whitelisted(name):
    return name.lower() in WHITELIST

def create_clean_tag(tag):
    '''Return an empty tag with whitelisted attributes, or None'''
    name = tag.name.lower()
    answer = element.Tag(name=name)
    # Special Case : encoding
    if name == 'meta':
        if (tag.get('http-equiv') == "Content-Type"
            and '7' not in tag.get('content')):
            answer['http-equiv'] = tag.get('http-equiv')
            answer['content'] = tag.get('content')
        else:
            return None
    # Special Case : input
    if name == 'input':
        if tag.get('type') in INPUT_TYPES:
            answer['type'] = tag.get('type')
        elif tag.get('type') in INPUT_BUTTON_TYPES:
            answer['type'] = 'button'
    for key, value in tag.attrs.iteritems():
        if (key in WHITELIST_ATTRS or 
            (name, key) in WHITELIST_NAME_ATTRS):
            answer[key] = value
        # Special Case : display:none
        elif key == 'style':
            if (not isinstance(value, list) and 
                re.search(r'display:\s*none', value)):
                answer['style'] = 'display:none'
    # Special Case : button
    if name == 'button':
        answer['type'] = 'button'
    return answer

def build(tag):
    '''Return the clean equivalent of tag, or None'''
    answer = create_clean_tag(tag)
    if not answer:
        return
    for child in tag.contents:
        #print tag.name, type(child), child.name, unicode([unicode(child)])[:50]
        if isinstance(child, element.Tag):
            if child.name.lower() in WHITELIST_NAMES:
                built_child = build(child)
                if built_child:
                    answer.append(built_child)
        elif child.__class__ == element.NavigableString:
            answer.append(unicode(child))
    return answer

def clean_html(page):
    '''Return the cleaned page as a unicode object.
    Argument:
    - page -- a page string without any <!--comment--> at the top.
    '''
    soup = BeautifulSoup(unicode(BeautifulSoup(page, "html5lib")), "html5lib")
    new_soup = BeautifulSoup('<!DOCTYPE html><html></html>')
    new_soup.html.replace_with(build(soup.html))
    document, errors = tidylib.tidy_document(unicode(new_soup))
    return document

if __name__ == '__main__':
    # Test
    from codecs import open
    with open(sys.argv[1]) as fin:
        url = fin.readline()
        data = fin.read()
    cleaned = clean_html(data)
    with open(sys.argv[2], 'w', 'utf8') as fout:
        fout.write(cleaned)
