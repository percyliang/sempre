# README

## Processed

* freebuild.def.json.gz contains just the 2495 definition queries in freebuild.json.gz

        gzcat freebuild.json.gz | grep '(:def'

* freebuild.json.gz is the main data file. Obtained by:

      awk '{print "{\"qid\":" NR "," substr($0,2)}' freebuildbig-0206 > freebuild.id.json
      jq -c '{"qid":.qid, "q":.q, "sessionId": .sessionId[:10], "time":.time}' freebuild.id.json > freebuild.json


## Raw queries

* freebuildbig-0206.def is the raw query log, without context

* qualifier3-0201: 30 turkers, 1 rejection

* qualifiers: turkers had to build a fixed target

* freebuild[12]: qualified turkers can build whatever they want
