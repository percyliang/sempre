package edu.stanford.nlp.sempre;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import edu.stanford.nlp.sempre.Example.Builder;
import fig.basic.LispTree;
import fig.basic.Pair;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Tracking where the rule comes from in the grammar induction process.
 *
 * @author Sida Wang
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleSource {
   @JsonProperty public String uid;
   @JsonProperty public LocalDateTime time;
   @JsonProperty public String head;
   @JsonProperty public List<String> body;

   @JsonProperty public int cite = 0;
   @JsonProperty public int self = 0;
   @JsonProperty public boolean isPrivate = true;
   
   public RuleSource(String uid, String head, List<String> body) {
     this.uid = uid;
     this.head = head;
     this.body = body;
     this.time = LocalDateTime.now();
   }
   public String toJson() { return Json.writeValueAsStringHard(this); }
}
