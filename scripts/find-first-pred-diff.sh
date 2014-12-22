#!/bin/sh
# Takes two log files and outputs the first line where they differ in terms 
# of the example or the predictions

egrep "Example: |Pred@| Item " ../state/execs/$1.exec/log > temp.1
egrep "Example: |Pred@| Item " ../state/execs/$2.exec/log > temp.2
cmp temp.1 temp.2
#rm temp.1
#rm temp.2

