package edu.stanford.nlp.sempre.roboy.error;

import edu.stanford.nlp.sempre.SimpleLexicon;
import edu.stanford.nlp.sempre.roboy.UnspecInfo;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;

import java.util.*;

import com.google.gson.Gson;
import fig.basic.LogInfo;

/**
 * Word2Vec to resolve underspecified types in the lexicon
 *
 * @author emlozin
 */
public class Word2VecRetriever extends KnowledgeRetriever {
    private Map<String, String> results;
    private Word2vec vec;
    public static Gson gson = new Gson();               /**< Gson object */

    public Word2VecRetriever(Word2vec vec){
        this.vec = vec;
        this.results = new HashMap<>();
    }

    public UnspecInfo analyze(UnspecInfo underTerm) {
        String entity = underTerm.term;
        UnspecInfo result = new UnspecInfo(entity, underTerm.type);
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
            }
        }
        return result;
    }
}
