package edu.stanford.nlp.sempre.paraphrase.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;

import fig.basic.Option;
import fig.basic.LogInfo;

public class Rulebase {

  public static class Options {
    @Option(gloss = "What rule types to use")
    public Set<String> ruleTypes;
  }
  public static Options opts = new Options();

  List<RuleApplier> rules;

  public static final String NULL_PHRASE = "(item (token [.*]*))";
  public static final String PHRASE = "(item (token [.*]+))";
  public static final String TOKEN = "(item (token [.*]))";

  //general delete and substitute rules
  public static final String DELETE_TOKEN = "(rule (expression "+ PHRASE + TOKEN + PHRASE + ") (rhs 0 2))";
  public static final String DELETE_PHRASE = "(rule (expression "+ PHRASE + PHRASE + PHRASE + ") (rhs 0 2))";
  public static final String DELETE_TWO_NOUNS = "(rule (expression "+ PHRASE + "(item (pos [NN.*]) (pos [NN.*]))" + PHRASE + ") (rhs 0 2))";
  public static final String SUBSTITUTE_TOKEN = "(rule (expression "+ PHRASE + TOKEN + PHRASE + ") (rhs 0 2))";
  public static final String SUBSTITUTE_PHRASE = "(rule (expression "+ PHRASE + PHRASE + PHRASE + ") (rhs 0 2))";
  public static final String SUBSTITUTE_TWO_NOUNS = "(rule (expression "+ PHRASE + "(item (pos [NN|NNS]) (pos [NN|NNS]))" + PHRASE + ") (rhs 0 2))";
  public static final String MOVE_PHRASE = "(rule (expression "+ PHRASE + PHRASE + PHRASE + ") (rhs 0 2))";

  //specific rules
  public static final String DELETE_DT = "(rule (expression (item (token [.*]*)) (item (pos [DT])) (item (token [.*]*))) (rhs 0 2))";
  public static final String DELETE_JJ = "(rule (expression (item (token [.*]*)) (item (pos [JJ])) (item (token [.*]*))) (rhs 0 2))";
  public static final String DELETE_RB = "(rule (expression (item (token [.*]*)) (item (pos [RB])) (item (token [.*]*))) (rhs 0 2))";
  public static final String DELETE_PRP = "(rule (expression (item (token [.*]*)) (item (pos [PRP])) (item (token [.*]*))) (rhs 0 2))";
  public static final String DELETE_DATE_1 = "(rule (expression (item (token [.*]*)) (item (pos [IN]) (ner [DATE])) (item (token [.*]*))) (rhs 0 2))";
  public static final String DELETE_DATE_2 = "(rule (expression (item (token [.*]*)) (item (ner [DATE])) (item (token [.*]*))) (rhs 0 2))";
  public static final String DELETE_AUX = "(rule (expression (item (token [.*]*)) (item (pos [VBD-AUX])) (item (token [.*]*))) (rhs 0 2))";
  //delete phrases
  public static final String NP = "(item (pos [DT]?)) (item (pos [JJ.*]*)) (item (pos [NN.*]+))";
  public static final String VERB = "(item (pos [VB.*])) (item (pos [RP|IN|TO]))";
  public static final String ENTITY = "(item (pos [NNP.*]+))";
  
  public static final String DELETE_NP = "(rule (expression " + NULL_PHRASE + NP + NULL_PHRASE + ") (rhs 0 4))";
  public static final String DELETE_PP = "(rule (expression " + NULL_PHRASE + " (item (pos [IN|TO])) " +NP + NULL_PHRASE + ") (rhs 0 5))";

  //nominalization
  public static final String NOM1 = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression (item (lemma [what]))",
        NP,
        VERB,
        ENTITY,
        "(item (token [.*])))",
        "(rhs 0 be;;be;;VBZ;;O;;O the;;the;;DT;;O;;OO 1 2 3 of;;of;;IN;;O;;O 6))"
        }));
  
  public static final String NOM2 = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression (item (lemma [what]))",
        NP,
        "(item (lemma [do]))",
        ENTITY,
        VERB,
        "(item (token [.*])))",
        "(rhs 0 be;;be;;VBZ;;O;;O the;;the;;DT;;O;;OO 1 2 3 of;;of;;IN;;O;;O 5))"
        }));
  
  //passive
  public static final String PASSIVE = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression",
        NULL_PHRASE,
        VERB,
        "(item (token [by]))",
        ENTITY,
        NULL_PHRASE+")",
        "(rhs 0 4 1 2 5))"
        }));
  
  public static final String POSSESSIVE_1 = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression",
        NULL_PHRASE,
        NP,
        "(item (token [of]))",
        NP,
        NULL_PHRASE+")",
        "(rhs 0 5 6 7 1 2 3 8))"
        }));
  
  public static final String POSSESSIVE_2 = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression",
        NULL_PHRASE,
        NP,
        "(item (token [of]))",
        NP,
        NULL_PHRASE+")",
        "(rhs 0 5 6 7 's;;'s;;POS;;O;;O 1 2 3 8))"
        }));
  //same syntactic rules but omitting the context  - just the match
//nominalization
  public static final String NOM1_ALIGN = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression (item (lemma [what]))",
        NP,
        VERB,
        ENTITY,
        "(item (token [.*])))",
        "(rhs be;;be;;VBZ;;O;;O the;;the;;DT;;O;;OO 1 2 3 of;;of;;IN;;O;;O))"
        }));
  
  public static final String NOM2_ALIGN = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression (item (lemma [what]))",
        NP,
        "(item (lemma [do]))",
        ENTITY,
        VERB,
        "(item (token [.*])))",
        "(rhs be;;be;;VBZ;;O;;O the;;the;;DT;;O;;OO 1 2 3 of;;of;;IN;;O;;O))"
        }));
  
  //passive
  public static final String PASSIVE_ALIGN = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression",
        NULL_PHRASE,
        VERB,
        "(item (token [by]))",
        ENTITY,
        NULL_PHRASE+")",
        "(rhs 4 1 2))"
        }));
  
  public static final String POSSESSIVE_1_ALIGN = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression",
        NULL_PHRASE,
        NP,
        "(item (token [of]))",
        NP,
        NULL_PHRASE+")",
        "(rhs 5 6 7 1 2 3))"
        }));
  
  public static final String POSSESSIVE_2_ALIGN = Joiner.on(' ').join(Arrays.asList(new String[]
      {"(rule (expression",
        NULL_PHRASE,
        NP,
        "(item (token [of]))",
        NP,
        NULL_PHRASE+")",
        "(rhs 5 6 7 's;;'s;;POS;;O;;O 1 2 3))"
        }));

  
  public Rulebase() {
    rules = new ArrayList<RuleApplier>();

    if(opts.ruleTypes.contains(RuleApplier.DELETE)) {
      rules.add(new DeletionRule(DELETE_TOKEN));
      rules.add(new DeletionRule(DELETE_TWO_NOUNS));
    }
    if(opts.ruleTypes.contains(RuleApplier.SUBST)) {
      rules.add(new SubstitutionRule(SUBSTITUTE_TOKEN));
      rules.add(new SubstitutionRule(SUBSTITUTE_TWO_NOUNS));
    }
    if(opts.ruleTypes.contains(RuleApplier.MOVE))
      rules.add(new MoveRule(MOVE_PHRASE));
    if(opts.ruleTypes.contains(RuleApplier.SYNTAX)) {
      LogInfo.log("Adding syntax rule");
      rules.add(new SyntacticRule(DELETE_PP));
      rules.add(new SyntacticRule(DELETE_DT));
      rules.add(new SyntacticRule(DELETE_JJ));
      rules.add(new SyntacticRule(DELETE_RB));
      rules.add(new SyntacticRule(DELETE_PRP));
      rules.add(new SyntacticRule(DELETE_AUX));
      rules.add(new SyntacticRule(NOM1));
      rules.add(new SyntacticRule(NOM2));
      rules.add(new SyntacticRule(PASSIVE));
      rules.add(new SyntacticRule(POSSESSIVE_1));
      rules.add(new SyntacticRule(POSSESSIVE_2));
    }
    if(opts.ruleTypes.contains(RuleApplier.PHRASE_TABLE)) {
      rules.add(new PhraseTableRule(SUBSTITUTE_PHRASE));
    }
    if(opts.ruleTypes.contains(RuleApplier.SYNT_SUBST)) {
      LogInfo.log("Adding syntactic rules");
      rules.add(new SyntacticRuleSet());
    }
    if(opts.ruleTypes.contains(RuleApplier.SYNT_ALIGN)) {
      LogInfo.log("Adding syntactic rules for alignment");
      rules.add(new SyntacticRule(NOM1_ALIGN));
      rules.add(new SyntacticRule(NOM2_ALIGN));
      rules.add(new SyntacticRule(PASSIVE_ALIGN));
      rules.add(new SyntacticRule(POSSESSIVE_1_ALIGN));
      rules.add(new SyntacticRule(POSSESSIVE_2_ALIGN));
    }
  }

  public List<RuleApplier> getRules() {
    return rules;
  }
}
