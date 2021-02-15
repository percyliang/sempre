package edu.stanford.nlp.sempre;

/**
 * Tools for colorizing output to console so easier to read
 *
 * @author Ziang Xie
 */

public class Colorizer {

  public Colorizer() { }

  public String colorize(String s, String color) {
    String cp = "";

    // NOTE JDK 7+ feature
    switch (color) {
      case "black":
        cp = "\u001B[30m";
        break;
      case "red":
        cp = "\u001B[31m";
        break;
      case "green":
        cp = "\u001B[32m";
        break;
      case "yellow":
        cp = "\u001B[33m";
        break;
      case "blue":
        cp = "\u001B[34m";
        break;
      case "purple":
        cp = "\u001B[35m";
        break;
      case "cyan":
        cp = "\u001B[36m";
        break;
      case "white":
        cp = "\u001B[37m";
        break;
      default:
        throw new RuntimeException("Invalid color: " + color);
    }

    if (cp.equals(""))
      return s;
    return cp + s + "\u001B[0m";
  }
}
