#!/usr/bin/env python

"""
Installs the needed dependencies for the community-server.

This should be called only from ./pull-dependencies community-server

Ideally, you would use virtualenv.
"""

import pip

print("Installing community-server dependencies...")

pip.main(['install', '-r', 'community-server/requirements.txt'])
