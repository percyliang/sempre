./run @mode=simple -Grammar.inPaths data/roboy-demo-talk.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-talk.examples \
	-Learner.maxTrainIters 10 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-demo-talk.lexicon
