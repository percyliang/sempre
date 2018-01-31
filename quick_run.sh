if [[ $1 = "web" ]]; then
    ./run @mode=simple -Grammar.inPaths data/roboy-dbpedia.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-dbpedia.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-dbpedia.lexicon \
	-server true
elif [[ $1 = "new" ]]; then
    ./run @mode=simple -Grammar.inPaths data/roboy-triple.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-dbpedia.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-dbpedia.lexicon \
	-server true
elif [[ $1 = "socket" ]]; then
    ./run @mode=socket -Grammar.inPaths data/roboy-dbpedia.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-dbpedia.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-dbpedia.lexicon
else
    ./run @mode=simple -Grammar.inPaths data/roboy-dbpedia.grammar \
  -FeatureExtractor.featureDomains rule \
  -Dataset.inPaths train:data/roboy-dbpedia.examples \
  -Learner.maxTrainIters 1 \
  -languageAnalyzer corenlp.CoreNLPAnalyzer \
  -SimpleLexicon.inPaths data/roboy-dbpedia.lexicon
fi
