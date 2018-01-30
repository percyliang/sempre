if [[ $1 = "web" ]]; then
    ./run @mode=simple -Grammar.inPaths data/roboy-dbpedia.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-dbpedia.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-dbpedia.lexicon \
	-server true
else
    ./run @mode=simple -Grammar.inPaths data/roboy-dbpedia.grammar \
  -FeatureExtractor.featureDomains rule \
  -Dataset.inPaths train:data/roboy-dbpedia.examples \
  -Learner.maxTrainIters 1 \
  -languageAnalyzer corenlp.CoreNLPAnalyzer \
  -SimpleLexicon.inPaths data/roboy-dbpedia.lexicon
fi
