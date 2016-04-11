#!/bin/bash
exit 1

################################################
# Make symlink 'phrase-predicate' in this directory and make a new subdirectory called 'out'
./ibm-align.py phrase-predicate > out/utterance-formula
./ibm-align.py phrase-predicate -z fixed > out/utterance-formula-fixed
./ibm-align.py phrase-predicate -z varied > out/utterance-formula-varied
./ibm-align.py phrase-predicate -z none > out/utterance-formula-none
./ibm-align.py phrase-predicate -s > out/formula-utterance
./ibm-align.py phrase-predicate -s -z fixed > out/formula-utterance-fixed
./ibm-align.py phrase-predicate -s -z varied > out/formula-utterance-varied
./ibm-align.py phrase-predicate -s -z none > out/formula-utterance-none
./find-average.py out/utterance-formula out/formula-utterance > out/average
./find-average.py out/utterance-formula-fixed out/formula-utterance-fixed > out/average-fixed
./find-average.py out/utterance-formula-varied out/formula-utterance-varied > out/average-varied
./find-average.py out/utterance-formula-none out/formula-utterance-none > out/average-none
