package edu.stanford.nlp.sempre.paraphrase.rules;

import java.util.regex.Pattern;

import fig.basic.LispTree;

public class LanguageExpToken {

  public enum TokenType {token, ner, pos, lemma}
  public enum RepeatType {NONE, Q_MARK, STAR, PLUS}

  public final TokenType type;
  public final RepeatType repeat;
  public final Pattern valuePattern;
  public final String value;

  public LanguageExpToken(String type, String value) {
    this.type = parseTokenType(type);
    this.valuePattern = Pattern.compile(value.substring(1,value.lastIndexOf(']')));
    this.repeat = parseRepeatType(value.endsWith("]") ? "" : value.substring(value.length()-1));
    this.value=value;
  }

  public static TokenType parseTokenType(String type) {
    if ("token".equals(type)) return TokenType.token;
    if ("lemma".equals(type)) return TokenType.lemma;
    if ("pos".equals(type)) return TokenType.pos;
    if ("ner".equals(type)) return TokenType.ner;
    throw new RuntimeException("Illegal token type: " + type);
  }
  
  public static RepeatType parseRepeatType(String repeat) {
    if("".equals(repeat)) return RepeatType.NONE;
    if("?".equals(repeat)) return RepeatType.Q_MARK;
    if("+".equals(repeat)) return RepeatType.PLUS;
    if("*".equals(repeat)) return RepeatType.STAR;
    throw new RuntimeException("Illegal repeat type: " + repeat);
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild(type.toString());
    tree.addChild(valuePattern.toString());
    return tree;
  }

  public String toString() {
    return toLispTree().toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((repeat == null) ? 0 : repeat.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    LanguageExpToken other = (LanguageExpToken) obj;
    if (repeat != other.repeat)
      return false;
    if (type != other.type)
      return false;
    if (value == null) {
      if (other.value != null)
        return false;
    } else if (!value.equals(other.value))
      return false;
    return true;
  }

}
