package edu.stanford.nlp.sempre.paraphrase.paralex;

import java.io.BufferedReader;
import java.io.IOException;

import edu.stanford.nlp.io.IOUtils;

/**
 * Takes the question file from paralex and reads questions
 * @author jonathanberant
 *
 */
public class ParalexQuestionReader {

  private String questionFile = "/u/nlp/data/semparse/paralex/wikianswers-paraphrases-1.0/questions.txt";
  private BufferedReader reader;

  public ParalexQuestionReader() throws IOException {
    reader = IOUtils.getBufferedFileReader(questionFile);
  }

  public String next() throws IOException {

    String res = null;
    boolean cont = true;
    while(cont) {
      String line = reader.readLine();
      if(line==null)
        cont=false;
      else {
        String[] tokens = line.split("\t");
        if(tokens.length==4) {
          cont=false;
          res = tokens[0];
        }
      }
    }
    return res;
  }

  public void close() throws IOException {
    reader.close();
  }



}
