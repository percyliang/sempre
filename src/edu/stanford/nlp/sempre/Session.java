package edu.stanford.nlp.sempre;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;

import fig.basic.Option;

/**
 * A Session contains the information specific to a user.
 * It maintains the context for discourse as well as the last example, so that
 * we can inspect the different predicted derivations and generate new training
 * examples / update parameters interactively.
 *
 * @author Percy Liang
 */
public class Session {
  public final String id;  // Session id
  public static class Options {
    // path for default parameters, if using a different set for each session
    @Option public String inParamsPath;
  }
  public String remoteHost;  // Where we connected from
  public String format;  // html or json
  public ContextValue context;  // Current context used to create new examples
  Example lastEx;  // Last example that we processed
  
  // if every user have their own model
  Params params;
  Learner learner;
  public Map<String,String> reqParams;
  
  public static Options opts = new Options();
  
  // per session parameters
  public Session(String id) {
    this.id = id;
    context = new ContextValue(id, DateValue.now(), new ArrayList<ContextValue.Exchange>());
  }

  public Example getLastExample() { return lastEx; }
  public String getLastQuery() { return lastEx == null ? null : lastEx.utterance; }

  public void updateContext() {
    context = context.withDate(DateValue.now());
  }

  public void updateContext(Example ex, int maxExchanges) {
    lastEx = ex;
    List<Derivation> derivations = lastEx.getPredDerivations();
    if (derivations.size() > 0) {
      Derivation deriv = derivations.get(0);
      List<ContextValue.Exchange> newExchanges = new ArrayList<ContextValue.Exchange>();
      newExchanges.addAll(context.exchanges);
      newExchanges.add(new ContextValue.Exchange(ex.utterance, deriv.formula, deriv.value));
      while (newExchanges.size() > maxExchanges)
        newExchanges.remove(0);
      context = context.withNewExchange(newExchanges);
    }
  }

  public void updateContextWithNewAnswer(Example ex, Derivation deriv) {
    List<ContextValue.Exchange> newExchanges = new ArrayList<ContextValue.Exchange>();
    for (int i = 0; i < context.exchanges.size() - 1; i++)
      newExchanges.add(context.exchanges.get(i));
    newExchanges.add(new ContextValue.Exchange(ex.utterance, deriv.formula, deriv.value));
    context = context.withNewExchange(newExchanges);
  }

  public ContextValue getContextExcludingLast() {
    List<ContextValue.Exchange> newExchanges = new ArrayList<ContextValue.Exchange>();
    for (int i = 0; i < context.exchanges.size() - 1; i++)
      newExchanges.add(context.exchanges.get(i));
    return context.withNewExchange(newExchanges);
  }
  
  public void useIndependentLearner(Builder builder) {
    this.params = new Params();
    if (!Strings.isNullOrEmpty(opts.inParamsPath))
      this.params.read(opts.inParamsPath);
    this.learner = new Learner(builder.parser, this.params, new Dataset());
  }
 
  @Override
  public String toString() {
    return String.format("%s: %s; last: %s", id, context, lastEx);
  }
  
  // Decides if we write out any logs
  public boolean isLogging() { return defaultTrue("logging");}
  public boolean isWritingCitation() { return defaultTrue("cite");}
  public boolean isWritingGrammar() { return defaultTrue("grammar");}
  public boolean isLearning() { return defaultTrue("learn");}
  public boolean isStatsing() { return defaultTrue("stats");}
  
  private boolean defaultTrue(String key) {
    if (this.reqParams == null) return true;
    if (!this.reqParams.containsKey(key)) return true;
    return !this.reqParams.get(key).equals("0");
  }
}
