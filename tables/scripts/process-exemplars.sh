#!/bin/bash
dir=${1:-exemplars}
dir=${dir%/}
mkdir -p $dir/$dir-csv
for x in $dir/$dir-json/*.json; do
  y=$(echo $x | sed 's+-json/\(.*\).json+-csv/\1.csv+')
  echo $x '-->' $y
  ./table-to-csv.py -J $x > $y || break
done
