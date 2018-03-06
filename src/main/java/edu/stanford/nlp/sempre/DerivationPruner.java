package edu.stanford.nlp.sempre;

import java.util.*;

import fig.basic.*;

/**
 * Prune derivations during parsing.
 *
 * To add custom pruning criteria, implement a DerivationPruningComputer class,
 * and put the class name in the |pruningComputers| option.
 *
 * @author ppasupat
 */

public class DerivationPruner {
  public static class Options {
    @Option(gloss = "Pruning strategies to use")
    public List<String> pruningStrategies = new ArrayList<>();
    @Option(gloss = "DerivationPruningComputer subclasses to look for pruning strategies")
    public List<String> pruningComputers = new ArrayList<>();
    @Option public int pruningVerbosity = 0;
    @Option(gloss = "(for tooManyValues) maximum denotation size of the final formula")
    public int maxNumValues = 10;
  }
  public static Options opts = new Options();

  public final Parser parser;
  public final Example ex;
  private List<DerivationPruningComputer> pruningComputers = new ArrayList<>();
  // If not null, limit the pruning strategies to this list in addition to opts.pruningStrategies.
  private List<String> customAllowedPruningStrategies;
  private final Set<String> allStrategyNames;

  public DerivationPruner(ParserState parserState) {
    this.parser = parserState.parser;
    this.ex = parserState.ex;
    this.pruningComputers.add(new DefaultDerivationPruningComputer(this));
    for (String pruningComputer : opts.pruningComputers) {
      try {
        Class<?> pruningComputerClass = Class.forName(SempreUtils.resolveClassName(pruningComputer));
        pruningComputers.add((DerivationPruningComputer) pruningComputerClass.getConstructor(this.getClass()).newInstance(this));
      } catch (ClassNotFoundException e1) {
        throw new RuntimeException("Illegal pruning computer: " + pruningComputer);
      } catch (Exception e) {
        e.printStackTrace();
        e.getCause().printStackTrace();
        throw new RuntimeException("Error while instantiating pruning computer: " + pruningComputer);
      }
    }
    // Compile the list of all strategies
    allStrategyNames = new HashSet<>();
    for (DerivationPruningComputer computer : pruningComputers)
      allStrategyNames.addAll(computer.getAllStrategyNames());
    for (String strategy : opts.pruningStrategies) {
      if (!allStrategyNames.contains(strategy))
        LogInfo.fails("Pruning strategy '%s' not found!", strategy);
    }
  }

  /**
   * Set additional restrictions on the pruning strategies.
   *
   * If customAllowedPruningStrategies is not null, the pruning strategy must be in both
   * opts.pruningStrategies and customAllowedPruningStrategies in order to be used.
   *
   * Useful when some pruning strategies can break the parsing mechanism.
   */
  public void setCustomAllowedPruningStrategies(List<String> customAllowedPruningStrategies) {
    this.customAllowedPruningStrategies = customAllowedPruningStrategies;
  }

  protected boolean containsStrategy(String name) {
    return opts.pruningStrategies.contains(name) &&
        (customAllowedPruningStrategies == null || customAllowedPruningStrategies.contains(name));
  }
  
  public List<DerivationPruningComputer> getPruningComputers() {
    return new ArrayList<>(pruningComputers);
  }

  /**
   * Return true if the derivation should be pruned. Otherwise, return false.
   */
  public boolean isPruned(Derivation deriv) {
    if (opts.pruningStrategies.isEmpty() && pruningComputers.isEmpty()) return false;
    String matchedStrategy;
    for (DerivationPruningComputer computer : pruningComputers) {
      if ((matchedStrategy = computer.isPruned(deriv)) != null) {
        if (opts.pruningVerbosity >= 2)
          LogInfo.logs("PRUNED [%s] %s", matchedStrategy, deriv.formula);
        return true;
      }
    }
    return false;
  }

  /**
   * Run isPruned with a (temporary) custom set of allowed pruning strategies.
   * If customAllowedPruningStrategies is null, all strategies are allowed.
   * If customAllowedPruningStrategies is empty, no pruning happens.
   */
  public boolean isPruned(Derivation deriv, List<String> customAllowedPruningStategies) {
    List<String> old = this.customAllowedPruningStrategies;
    this.customAllowedPruningStrategies = customAllowedPruningStategies;
    boolean answer = isPruned(deriv);
    this.customAllowedPruningStrategies = old;
    return answer;
  }

}
