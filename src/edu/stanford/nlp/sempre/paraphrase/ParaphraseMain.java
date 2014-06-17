package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.fbalignment.lexicons.EntityLexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.Lexicon;
import edu.stanford.nlp.sempre.fbalignment.lexicons.WordDistance;
import edu.stanford.nlp.sempre.paraphrase.paralex.ParalexRules;
import edu.stanford.nlp.sempre.paraphrase.paralex.PhraseTable;
import edu.stanford.nlp.sempre.paraphrase.rules.RuleApplier;
import edu.stanford.nlp.sempre.paraphrase.rules.Rulebase;
import edu.stanford.nlp.sempre.paraphrase.rules.SubstitutionRuleExtractor;
import edu.stanford.nlp.sempre.paraphrase.rules.SyntacticRuleSet;
import edu.stanford.nlp.sempre.paraphrase.rules.VerbSemClassExtractor;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.OptionsParser;
import fig.exec.Execution;

import java.io.IOException;

public class ParaphraseMain implements Runnable {

  @Option (gloss="run mode") public String mode="train";
  @Option (gloss="whether to visualize contexts to formulas") public boolean visualize=false;

  public void run() {

    LogInfo.logs("run mode: %s",mode);
      if(mode.equals("train")) {
        ParaphraseBuilder tBuilder = new ParaphraseBuilder();
        tBuilder.build();
        ParaphraseDataset paraphraseDataset = new ParaphraseDataset();
        paraphraseDataset.read();
        try {
          ParaphraseLearner paraphraseLearner = new ParaphraseLearner(tBuilder.params, paraphraseDataset, tBuilder.executor);
          paraphraseLearner.learn();
        } catch(IOException e) {
          throw new RuntimeException(e);
        }
      }
      else throw new RuntimeException("Unknown mode: " + mode);
  }

  public static void main(String[] args) {

    OptionsParser parser = new OptionsParser();
    parser.registerAll(
            new Object[]{
                    "Master", Master.opts,
                    "ParaphraseBuilder", ParaphraseBuilder.opts,
                    "NnBuilder",NnBuilder.opts,
                    "NearestNeighborLearner", NearestNeighborLearner.opts,
                    "Grammar", Grammar.opts,
                    "Derivation", Derivation.opts,
                    "Parser", Parser.opts,
                    "BeamParser", BeamParser.opts,
                    "SparqlExecutor", SparqlExecutor.opts,
                    "Dataset", Dataset.opts,
                    "Params", Params.opts,
                    "SemanticFn", SemanticFn.opts,
                    "LexiconFn", LexiconFn.opts,
                    "Lexicon", Lexicon.opts,
                    "JoinFn", JoinFn.opts,
                    "DescriptionValue", DescriptionValue.opts,
                    "FeatureExtractor", FeatureExtractor.opts,
                    "LanguageInfo", LanguageInfo.opts,
                    "EntityLexicon", EntityLexicon.opts,
                    "FreebaseInfo", FreebaseInfo.opts,
                    "BridgeFn", BridgeFn.opts,
                    "WordDistance", WordDistance.opts,
                    "ContextMapper", ContextModel.opts,
                    "ParalexRules", ParalexRules.opts,
                    "ContextModel", ContextModel.opts,
                    "ContextSimilarityModel", ContextSimilarityModel.opts,
                    "SubstitutionRuleExtractor", SubstitutionRuleExtractor.opts,
                    "ParaphraseDataset",ParaphraseDataset.opts,
                    "ParaphraseLearner", ParaphraseLearner.opts,
                    "ParaphraseFeatureMatcher", ParaphraseFeatureMatcher.opts,
                    "Rulebase",Rulebase.opts,
                    "FormulaRetriever",FormulaRetriever.opts,
                    "RuleApplier",RuleApplier.opts,
                    "ParaphraseParser",ParaphraseParser.opts,
                    "VectorSpaceModel",VectorSpaceModel.opts,
                    "SyntacticRuleSet",SyntacticRuleSet.opts,
                    "Aligner",Aligner.opts,
                    "QuestionGenerator",QuestionGenerator.opts,
                    "PhraseTable",PhraseTable.opts,
                    "FeatureSimilarityComputer",FeatureSimilarityComputer.opts,
            });
    Execution.run(args, "ParaphraseMain", new ParaphraseMain(), parser);
  }
}
