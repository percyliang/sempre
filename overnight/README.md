# Files for Building a Semantic Parser Overnight

## Setting up datasets

To generate canonical utterances, run

      ./pull-dependencies freebase overnight
      make corenlp freebase overnight
      ./run @mode=genovernight-wrapper

Or, to generate each individual domain
      ./run @mode=genovernight @gen=1 @domain=<domain>

After turking and setting up the approprate example files, run

      ./run @mode=overnight @domain=<domain>

to run with all features.

To run with partial features, use the following commands:

      Baseline - ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match skip-bigram root lf simpleworld
      No Lexical features - ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match ppdb skip-bigram root lf simpleworld
      No PPDB - ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match skip-bigram root lf alignment lexical root_lexical simpleworld
      Full system - ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match ppdb skip-bigram root alignment lexical root_lexical lf simpleworld
