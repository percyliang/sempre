package edu.stanford.nlp.sempre;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tracking where the rule comes from in the grammar induction process.
 *
 * @author sidaw
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleSource {
  @JsonProperty
  public String uid;
  @JsonProperty
  public LocalDateTime time;
  @JsonProperty
  public String head;
  @JsonProperty
  public List<String> body;

  @JsonProperty
  public int cite = 0;
  @JsonProperty
  public int self = 0;
  @JsonProperty
  public boolean align = false;
  @JsonProperty
  public String alignInfo = "";

  public RuleSource(String uid, String head, List<String> body) {
    this.uid = uid;
    this.head = head;
    this.body = body;
    this.time = LocalDateTime.now();
  }

  public String toJson() {
    return Json.writeValueAsStringHard(this);
  }

}
