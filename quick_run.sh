if  [[ $1 = "freebase" ]]; then
    ./run @mode=simple -Grammar.inPaths data/roboy-freebase-talk.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-talk.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths freebase/data/tutorial-freebase.lexicon \
	-server true
elif [[ $1 = "demo" ]]; then
    ./run @mode=socket -Grammar.inPaths data/roboy-demo-talk.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-talk.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-demo-talk.lexicon \
	-server true
else
    ./run @mode=simple -Grammar.inPaths data/roboy-talk.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-talk.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-demo-talk.lexicon \
	-server true
fi



