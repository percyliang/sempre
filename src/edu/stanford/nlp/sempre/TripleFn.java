package edu.stanford.nlp.sempre;

import java.util.*;
import fig.basic.LispTree;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Takes three strings and returns triple build from them
 *
 * @author Emilia Lozinska
 */

public class TripleFn extends SemanticFn {
    public String mode;

    public TripleFn() { }

    public TripleFn(String mode) {
        this.mode = mode;
    }

    public void init(LispTree tree) {
        super.init(tree);
        mode = tree.child(1).value;
    }

    public String initial_formula(String string1, String string2) {
        Map<String,Object> triple = new HashMap<>();
        if (this.mode.equals("spo")) {
            triple.put("subject", string1);
            triple.put("predicate", string2);
            System.out.println("HELLO");
        }
        else if (this.mode.equals("sop")) {
            triple.put("subject", string1);
            triple.put("object", string2);
        }
        else if (this.mode.equals("pso")) {
            triple.put("predicate", string1);
            triple.put("subject", string2);
        }
        else if (this.mode.equals("pos")) {
            triple.put("predicate", string1);
            triple.put("object", string2);
        }
        else if (this.mode.equals("osp")) {
            triple.put("object", string1);
            triple.put("subject", string2);
        }
        else if (this.mode.equals("ops")) {
            triple.put("object", string1);
            triple.put("predicate", string2);
        }

        // Convert a Map into JSON string.
        Gson gson = new Gson();
        String str_triple = gson.toJson(triple);
        System.out.println("Triple = " + str_triple);
        return str_triple;
    }

    public String final_formula(String string1, String string2) {
        System.out.println(string1);
        // Convert JSON string back to Map.
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> triple = gson.fromJson(string1, type);
/*        Map<String, String> triple = new HashMap<String,String>();
        triple = (Map<String,String>) gson.fromJson(string1, triple.getClass());*/

        if (this.mode.equals("spo")) {
            triple.put("object", string2);
        }
        else if (this.mode.equals("sop")) {
            triple.put("predicate", string2);
        }
        else if (this.mode.equals("pso")) {
            triple.put("object", string2);
        }
        else if (this.mode.equals("pos")) {
            triple.put("subject", string2);
        }
        else if (this.mode.equals("osp")) {
            triple.put("predicate", string2);
        }
        else if (this.mode.equals("ops")) {
            triple.put("subject", string2);
        }
        // split multiple subjects
        String[] subject = split(triple.get("subject").toString());
        String[] predicate = split(triple.get("predicate").toString());
        String[] object = split(triple.get("object").toString());

        StringBuilder out = new StringBuilder();
        for (String s : subject){
            for (String p : predicate) {
                for (String o : object) {
                    if (out.toString().length() > 0) out.append(",");
                    out.append("(");
                    out.append(s);
                    out.append(",");
                    out.append(p);
                    out.append(",");
                    out.append(o);
                    out.append(")");
                }
            }
        }

        return out.toString();
    }

    public DerivationStream call(Example ex, final Callable c) {
        return new SingleDerivationStream() {
            @Override
            public Derivation createDerivation() {
                String out = new String();
                if (c.childStringValue(0).contains(",")){
                    out = final_formula(c.childStringValue(0),c.childStringValue(1));
                }
                else{
                    out = initial_formula(c.childStringValue(0),c.childStringValue(1));
                }
                return new Derivation.Builder()
                        .withCallable(c)
                        .withStringFormulaFrom(out)
                        .createDerivation();
            };
        };
    }


    public String[] split(String str){
        str = str.substring(str.indexOf("("),str.indexOf(")"));
        if (str.contains(";")){
            return str.split(";");
        }
        else if (str.contains(",")){
            return str.split(",");
        }
        else {
            String[] result = new String[] {str};
            return result;
        }
    }
}
