#!/usr/bin/env python

"""
Installs the needed dependencies for the community-server.

Ideally, you would use virtualenv.
"""

import pip

print("Installing community-server dependencies...")

pip.main(['install', '-r', 'requirements.txt'])
