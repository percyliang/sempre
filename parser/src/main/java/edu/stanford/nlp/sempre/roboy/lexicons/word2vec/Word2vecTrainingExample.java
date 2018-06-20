package edu.stanford.nlp.sempre.roboy.lexicons.word2vec;

import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;

import java.util.Collection;


/**
 * Neural net that processes text into word-vectors.
 *
 * Adapted from org.deeplearning4j.examples.nlp.word2vec.Word2VecRawTextExample
 */
public class Word2vecTrainingExample {

    public static void main(String[] args) throws Exception {
        ToyDataGetter dataGetter = new ToyDataGetter(true);
        dataGetter.ensureToyDataIsPresent();
        String dataPath = dataGetter.getToyDataFilePath();

        // load and preprocess data

        System.out.println("Load & Vectorize Sentences....");
        // Strip white space before and after for each line
        SentenceIterator iter = new BasicLineIterator(dataPath);
        // Split on white spaces in the line to get words
        TokenizerFactory t = new DefaultTokenizerFactory();

        /*
            CommonPreprocessor will apply the following regex to each token: [\d\.:,"'\(\)\[\]|/?!;]+
            So, effectively all numbers, punctuation symbols and some special symbols are stripped off.
            Additionally it forces lower case for all tokens.
         */
        t.setTokenPreProcessor(new CommonPreprocessor());


        System.out.println("Building model....");
        Word2Vec vec = new Word2Vec.Builder()
                .minWordFrequency(10)
                .iterations(5)
                .layerSize(400)
                .seed(42)
                .windowSize(5)
                .iterate(iter)
                .tokenizerFactory(t)
                .build();

        System.out.println("Fitting Word2Vec model....");
        vec.fit();

        // Prints out the closest 10 words to "day". An example on what to do with these Word Vectors.
        Collection<String> lst = vec.wordsNearest("day", 10);
        System.out.println("10 Words closest to 'day': " + lst);

        double cosSim = vec.similarity("spring", "autumn");
        System.out.println(cosSim);
        cosSim = vec.similarity("summer", "winter");
        System.out.println(cosSim);

        System.out.println("Save vectors....");
        WordVectorSerializer.writeWord2VecModel(vec, dataGetter.getOutputFilePath());

    }


}