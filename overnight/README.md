# Files for Building a Semantic Parser Overnight

## Generating canonical utterances

To generate all the canonical utterances, run:

      ./pull-dependencies freebase overnight
      ant corenlp freebase overnight
      ./run @mode=genovernight-wrapper

To generate each individual domain:

      ./run @mode=genovernight @gen=1 @domain=<domain>

## Training

After generating the paraphrases via AMT and setting up the appropriate example
files, we train a model.

Run the following to train with all the features:

      ./run @mode=overnight @domain=<domain>

To run with a subset of the features for ablation studies:

Baseline:

    ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match skip-bigram root lf simpleworld

No Lexical features:

    ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match ppdb skip-bigram root lf simpleworld

No PPDB features:

    ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match skip-bigram root lf alignment lexical root_lexical simpleworld

Full system:

    ./run @mode=overnight @domain=<domain> -OvernightFeatureComputer.featureDomains match ppdb skip-bigram root alignment lexical root_lexical lf simpleworld
