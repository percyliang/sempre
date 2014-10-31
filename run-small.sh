trap 'pkill virtuoso' EXIT

./scripts/virtuoso start lib/freebase/93.exec/vdb 3093
./parasempre @mode=train \
    @sparqlserver=localhost:3093 \
    @domain=small \
    @cacheserver=local \
    -ParaphraseLearner.numOfThreads 2

pkill virtuoso
echo 'Finished run on small dataset'

