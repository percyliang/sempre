if [[ $1 = "web" ]]; then
    ./run @mode=simple -Grammar.inPaths data/roboy-dbpedia.grammar \
	-FeatureExtractor.featureDomains rule \
	-Dataset.inPaths train:data/roboy-dbpedia.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-SimpleLexicon.inPaths data/roboy-dbpedia.lexicon \
	-server true
elif [[ $1 = "db_offline" ]]; then
    ./run @mode=database-server \
	@sparqlserver=localhost:3001 \
	-Grammar.inPaths data/roboy-triple.grammar \
	-SimpleLexicon.inPaths data/lexicons/* \
	-Dataset.inPaths train:data/people.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer\
	-server true
elif [[ $1 = "db_online" ]]; then
    ./run @mode=database-server \
	-Grammar.inPaths data/roboy-triple.grammar \
	-SimpleLexicon.inPaths data/lexicons/* \
	-Dataset.inPaths train:data/short.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-server true
elif [[ $1 = "db_simple" ]]; then
    ./run @mode=simple \
	-Grammar.inPaths data/roboy-triple.grammar \
	-SimpleLexicon.inPaths data/lexicons/* \
	-Dataset.inPaths train:data/short.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-server true
elif [[ $1 = "db_demo" ]]; then
    ./run @mode=socket \
	-Grammar.inPaths data/roboy-demo.grammar \
	-SimpleLexicon.inPaths data/lexicons/* \
	-Dataset.inPaths train:data/short.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer
elif [[ $1 = "db_demo_interactive" ]]; then
    ./run @mode=simple \
	-Grammar.inPaths data/roboy-demo.grammar \
	-SimpleLexicon.inPaths data/lexicons/* \
	-Dataset.inPaths train:data/short.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-server true
elif [[ $1 = "db_demo_online" ]]; then
    ./run @mode=database-server \
	-Grammar.inPaths data/roboy-demo.grammar \
	-SimpleLexicon.inPaths data/lexicons/* \
	-Dataset.inPaths train:data/short.examples \
	-Learner.maxTrainIters 1 \
	-languageAnalyzer corenlp.CoreNLPAnalyzer \
	-server true
elif [[ $1 = "freebase" ]]; then
    ./run @mode=database-server \
	@sparqlserver=localhost:3001 \
	-Grammar.inPaths freebase/data/tutorial-freebase.grammar \
	-SimpleLexicon.inPaths freebase/data/tutorial-freebase.lexicon \
	-languageAnalyzer corenlp.CoreNLPAnalyzer
else
    ./run @mode=simple -Grammar.inPaths data/roboy-dbpedia.grammar \
  -FeatureExtractor.featureDomains rule \
  -Dataset.inPaths train:data/roboy-dbpedia.examples \
  -Learner.maxTrainIters 1 \
  -languageAnalyzer corenlp.CoreNLPAnalyzer \
  -SimpleLexicon.inPaths data/roboy-dbpedia.lexicon
fi
