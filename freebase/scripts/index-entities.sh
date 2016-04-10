#!/bin/bash
# arg0 - entity info file (/user/jobernat/scr/fb_data/aug_13/entityInfo.txt
# arg1 - target dir (/u/nlp/data/semparse/lucene/4.4/inexact
# arg2 - indexing strategy (inexact)

# TODO: document; move into lexicon
usage() {
  echo "Usage: $0 (entityFile) (indexDir) (indexStrategy)" 
  exit 1
}

if [ $# -lt 3 ]; then
  usage
fi
java -Xmx3g -cp "classes:lib/*" edu.stanford.nlp.sempre.fbalignment.index.FbEntityIndexer $1 $2 $3 
