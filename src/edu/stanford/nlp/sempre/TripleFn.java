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

    public String merge_formula(String string1, String string2) {
        // Convert JSON string back to Map.
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> triple = gson.fromJson(string1, type);

        // Convert JSON string back to Map.
        Map<String, String> triple2 = gson.fromJson(string2, type);

        Map<String, String> result = new HashMap<String, String>();

        result.putAll(triple);
        for (Map.Entry<String, String> entry : triple2.entrySet())
        {

            System.out.println("Entry:" + entry.toString());
            System.out.println(entry.getValue() + " "+result.get(entry.getKey()));
            if(result.containsKey(entry.getKey()) && !(entry.getValue().equals(result.get(entry.getKey()))))
                result.put(entry.getKey(),result.get(entry.getKey()).concat(","+entry.getValue()));
            else
                result.put(entry.getKey(),entry.getValue());
        }
        System.out.println(string2);
        System.out.println(triple.toString());
        System.out.println(triple2.toString());
        System.out.println(result.toString());

        // Convert a Map into JSON string.
        String str_triple = gson.toJson(result);
        System.out.println("MERGE triple = " + str_triple);
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


        if (triple.get("subject").toString().contains(";")||triple.get("subject").toString().contains(","))
            out.append("(");
/*        if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
            out.append("(");
        if (triple.get("object").toString().contains(";")||triple.get("object").toString().contains(","))
            out.append("(");*/
        for (String s : subject){
            if (out.toString().length() > 1 && triple.get("subject").toString().contains(","))
                out.append(",");
            if (out.toString().length() > 1 && triple.get("subject").toString().contains(";"))
                out.append(";");
            if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
                out.append("(");
            StringBuilder out2 = new StringBuilder();
            for (String p : predicate) {
                if (out2.toString().length() > 0 && triple.get("predicate").toString().contains(","))
                    out2.append(",");
                if (out2.toString().length() > 0 && triple.get("predicate").toString().contains(";"))
                    out2.append(";");
                StringBuilder out3 = new StringBuilder();
                for (String o : object) {
                    if (out3.toString().length() > 0 && triple.get("object").toString().contains(","))
                        out3.append(",");
                    if (out3.toString().length() > 0 && triple.get("object").toString().contains(";"))
                        out3.append(";");
                    out3.append("(");
                    out3.append(s);
                    out3.append(",");
                    out3.append(p);
                    out3.append(",");
                    out3.append(o);
                    out3.append(")");
                    System.out.println("Out3"+out3.toString());
                }
                out2.append(out3.toString());
                System.out.println("Out2"+out2.toString());
            }
            out.append(out2.toString());
            System.out.println("Out"+out.toString());
            if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
                out.append(")");
        }
        if (triple.get("subject").toString().contains(";")||triple.get("subject").toString().contains(","))
            out.append(")");
/*        if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
            out.append(")");
        if (triple.get("object").toString().contains(";")||triple.get("object").toString().contains(","))
            out.append(")");   */

        return out.toString();
    }

    public DerivationStream call(Example ex, final Callable c) {
        return new SingleDerivationStream() {
            @Override
            public Derivation createDerivation() {
                String out = new String();
                if (c.childStringValue(0).contains("{") && c.childStringValue(1).contains("{")){
                    out = merge_formula(c.childStringValue(0),c.childStringValue(1));
                }
                else if (c.childStringValue(0).contains("{")){
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
        if (str.contains("("))
            str = str.substring(str.indexOf("(")+1,str.indexOf(")"));
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
