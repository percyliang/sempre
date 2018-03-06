package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import fig.basic.LogInfo;
import fig.basic.MapUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actually does the parsing.  Main method is infer(), whose job is to fill in
 *
 * @author Roy Frostig
 * @author Percy Liang
 */
public abstract class ChartParserState extends ParserState {
  // cell (start, end, category) -> list of derivations (sorted by decreasing score) [beam]
  protected final Map<String, List<Derivation>>[][] chart;

  // For visualizing how chart is filled
  protected List<CatSpan> chartFillingList = new ArrayList<>();

  protected String[][] phrases; // the phrases in the example

  @SuppressWarnings({ "unchecked" })
  public ChartParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);

    // Initialize the chart.
    this.chart = (HashMap<String, List<Derivation>>[][])
        Array.newInstance(HashMap.class, numTokens, numTokens + 1);
    this.phrases = new String[numTokens][numTokens + 1];

    for (int start = 0; start < numTokens; start++) {
      StringBuilder sb = new StringBuilder();
      for (int end = start + 1; end <= numTokens; end++) {
        if (end - start > 1)
          sb.append(' ');
        sb.append(this.ex.languageInfo.tokens.get(end - 1));
        phrases[start][end] = sb.toString();
        chart[start][end] = new HashMap<>();
      }
    }
  }

  public void clearChart() {
    for (int start = 0; start < numTokens; start++) {
      for (int end = start + 1; end <= numTokens; end++) {
        chart[start][end].clear();
      }
    }
  }

  // Call this method in infer()
  protected void setPredDerivations() {
    predDerivations.clear();
    predDerivations.addAll(MapUtils.get(chart[0][numTokens], Rule.rootCat, Derivation.emptyList));
  }

  private void visualizeChart() {
    for (int len = 1; len <= numTokens; ++len) {
      for (int i = 0; i + len <= numTokens; ++i) {
        for (String cat : chart[i][i + len].keySet()) {
          List<Derivation> derivations = chart[i][i + len].get(cat);
          for (Derivation deriv : derivations) {
            LogInfo.logs("ParserState.visualize: %s(%s:%s): %s", cat, i, i + len, deriv);
          }
        }
      }
    }
  }

  protected void addToChart(Derivation deriv) {
    if (parser.verbose(3)) LogInfo.logs("addToChart %s: %s", deriv.cat, deriv);

    if (Parser.opts.pruneErrorValues && deriv.value instanceof ErrorValue) return;

    List<Derivation> derivations = chart[deriv.start][deriv.end].get(deriv.cat);
    if (chart[deriv.start][deriv.end].get(deriv.cat) == null)
      chart[deriv.start][deriv.end].put(deriv.cat, derivations = new ArrayList<>());
    derivations.add(deriv);
    totalGeneratedDerivs++;

    if (Parser.opts.visualizeChartFilling) {
      chartFillingList.add(new CatSpan(deriv.start, deriv.end, deriv.cat));
    }
  }

  public Map<String, List<Derivation>>[][] getChart() {
    return chart;
  }

  // TODO(joberant): move to visualization utility class
  public static class CatSpan {
    @JsonProperty
    public final int start;
    @JsonProperty public final int end;
    @JsonProperty public final String cat;

    @JsonCreator
    public CatSpan(@JsonProperty("start") int start, @JsonProperty("end") int end,
                   @JsonProperty("cat") String cat) {
      this.start = start;
      this.end = end;
      this.cat = cat;
    }
  }

  public static class ChartFillingData {
    @JsonProperty public final String id;
    @JsonProperty public final String utterance;
    @JsonProperty public final int numOfTokens;
    @JsonProperty public final List<CatSpan> catSpans;

    @JsonCreator
    public ChartFillingData(@JsonProperty("id") String id, @JsonProperty("catspans") List<CatSpan> catSpans,
                            @JsonProperty("utterance") String utterance, @JsonProperty("numOfTokens") int numOfTokens) {
      this.id = id;
      this.utterance = utterance;
      this.numOfTokens = numOfTokens;
      this.catSpans = catSpans;
    }
  }
}
