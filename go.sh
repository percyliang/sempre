#!/bin/bash
#

# ===========================================
# Parameters for Sun Grid Engine submition
# ============================================

# Name of job
#$ -N parasempre

# Shell to use
#$ -S /bin/bash

# All paths relative to current working directory
#$ -cwd

# List of queues
# #$ -q serial.q
#$ -q 'nlp-amd,serial.q,inf.q,eng-inf_parallel.q'

# Define parallel environment for multicore processing
#$ -pe openmp 16

# Send mail to. (Comma separated list)
#$ -M dc34@sussex.ac.uk

# When: [b]eginning, [e]nd, [a]borted and reschedules, [s]uspended, [n]one
#$ -m beas

# Validation level (e = reject on all problems)
#$ -w e

# Merge stdout and stderr streams: yes/no
#$ -j yes

module add jdk/1.7.0_51_openjdk

java -version

echo 'Beginning experiment'

trap "pkill virtuoso" SIGHUP SIGINT SIGTERM

./scripts/virtuoso start lib/freebase/93.exec/vdb 3093
./parasempre @mode=train \
    @sparqlserver=localhost:3093 \
    @domain=webquestions \
    @cacheserver=local \
    -ParaphraseLearner.numOfThreads 16

pkill virtuoso

echo 'Experiment complete'

