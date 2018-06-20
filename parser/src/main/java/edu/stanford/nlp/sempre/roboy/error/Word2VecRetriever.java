package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.SimpleLexicon;
import edu.stanford.nlp.sempre.roboy.UnderspecifiedInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;

import java.io.PrintWriter;
import java.util.*;

import com.google.gson.Gson;
import fig.basic.IOUtils;
import fig.basic.LogInfo;

/**
 * Word2Vec to resolve underspecified types in the lexicon
 *
 * @author emlozin
 */
public class Word2VecRetriever extends KnowledgeRetriever {
    public static Gson gson = new Gson();                   /**< Gson object */

    private Word2vec vec;                                   /**< Word2Vec model link*/

    public PrintWriter out;
    /**
     * Constructor
     */
    public Word2VecRetriever(Word2vec vec){
        this.vec = vec;
        out = IOUtils.openOutAppendHard("./resources_nlu/error_test/w2vLexicon.txt");
    }

    /**
     * Analyzer retrieving new candidates
     *
     * @param underTerm   information about the candidates for underspecified term
     */
    public UnderspecifiedInfo analyze(UnderspecifiedInfo underTerm) {
        String entity = underTerm.term;
        UnderspecifiedInfo result = new UnderspecifiedInfo(entity, underTerm.type);
        List<String> known_words= new ArrayList<String>(SimpleLexicon.getSingleton().lookup_type(entity));
        List<String> candidate = this.vec.getBest(entity,known_words);
        for (String c: candidate){
            Map<String,String> record = new HashMap();
            record.put("Label",c);
            record.put("Refcount",Double.toString(this.vec.getSimilarity(entity,c)));
            List<SimpleLexicon.Entry> entries = SimpleLexicon.getSingleton().lookup(c);
            for (SimpleLexicon.Entry entry:entries) {
                record.put("URI",entry.formula.toString());
                result.candidates.add(entry.formula.toString());
                result.candidatesInfo.add(this.gson.toJson(record));
                if (ConfigManager.DEBUG > 3)
                    LogInfo.logs("Word2Vec: %s",record.toString());

                out.println(record.toString());
            }
        }
        return result;
    }
}
