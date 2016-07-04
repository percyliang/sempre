- create a zip file
- update `README.md`
- add `README.md`, `t/data/` and `t/csv/`
- rsync the html data from the cluster and then add to zip file
- run the following:

./run @mode=tables @class=ann-data @data=none -Dataset.inPaths train,t/data/training.examples dev,t/data/pristine-seen-tables.examples test,t/data/pristine-unseen-tables.examples -splitdevfromtrain false
./run @mode=tables @class=ann-table

and then put the results in `annotated`
