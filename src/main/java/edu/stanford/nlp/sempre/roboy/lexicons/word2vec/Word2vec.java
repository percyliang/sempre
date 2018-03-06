package edu.stanford.nlp.sempre.roboy.lexicons.word2vec;

import fig.basic.LogInfo;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
//import org.deeplearning4j.exception;
//import org.apache.commons.io;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;


/**
 * Neural net that processes text into word-vectors.
 *
 * Adapted from org.deeplearning4j.examples.nlp.word2vec.Word2VecRawTextExample
 */
public class Word2vec {
    private final ToyDataGetter dataGetter;
    private String modelPath;
    private final Word2Vec vec;
    private final WeightLookupTable weightLookupTable;
    private final Iterator<INDArray> vectors;
    private final double threshold;

    public Word2vec() throws Exception {
        InputStream input = new FileInputStream("config.properties");
        Properties prop = new Properties();
        prop.load(input);
        threshold = Double.parseDouble(prop.getProperty("W2V_THRES"));

        // Get all files paths
        // LogInfo.logs("Loading files...");
        this.dataGetter = new ToyDataGetter(false);
        // LogInfo.logs("Loading files...");
        this.dataGetter.ensureToyModelIsPresent();
        // LogInfo.logs("Loading files...");
        this.modelPath = this.dataGetter.getToyModelFilePath();

        // Load model
        // LogInfo.logs("Loading models...");
        File gModel = new File(modelPath);
        this.vec = WordVectorSerializer.readWord2VecModel(gModel);

        // Weight tables
        // LogInfo.logs("Weight tables...");
        this.weightLookupTable = this.vec.lookupTable();
        this.vectors = weightLookupTable.vectors();

        LogInfo.begin_track("Tests -> ");
        LogInfo.logs("Closest words: %s", this.getClosest("queen",10));
        LogInfo.logs("Closest word to female from : \"women\",\"queen\",\"elisabeth\" -> %s", this.getBest("female", Arrays.asList("women","queen","elisabeth")));
        LogInfo.logs("Closest word to swimming from : \"literature\",\"music\",\"sports\" -> : %s",this.getBest("swimming", Arrays.asList("literature","music","sports")));
        LogInfo.end_track();
    }

    public Collection<String> getClosest(String word, int number){
        return this.vec.wordsNearest(word, number);
    }

    public double getSimilarity(String arg1, String arg2){
        return this.vec.similarity(arg1, arg2);
    }

    public List<String> getBest(String arg1, List<String> list_words){
        List<String> best = new ArrayList<String>();
        for (String word: list_words){
            if (threshold < this.vec.similarity(arg1, word)){
                best.add(word);
            }
        }
        return best;
    }

    public INDArray getMatrix(String word){
        return this.vec.getWordVectorMatrix(word);
    }

    public double[] getWordVector(String word){
        return this.vec.getWordVector(word);
    }

    public static void main(String[] args) throws Exception {
        try{
            Word2vec vec = new Word2vec();
            LogInfo.logs("Tests -> ");
            System.out.println("Similarity check: "+vec.getSimilarity("elisabeth", "elisa"));
            System.out.println("Similarity check simple: "+"elisa".compareToIgnoreCase("elisabeth"));
            System.out.println("Closest words: "+vec.getClosest("queen",10));
            System.out.println("Closest word to female from : \"women\",\"queen\",\"elisabeth\" -> "+vec.getBest("female", Arrays.asList("women","queen","elisabeth")));
            System.out.println("Closest word to swimming from : \"literature\",\"music\",\"sports\" -> : "+vec.getBest("swimming", Arrays.asList("literature","music","sports")));

//            System.out.println("Word vector: "+ Arrays.toString(vec.getWordVector("day")));
//            System.out.println("Word vector: "+vec.getMatrix("day"));
        }catch(Exception e){
            System.out.println("Exception in Word2Vec: "+e.getMessage());
        }

    }

}
