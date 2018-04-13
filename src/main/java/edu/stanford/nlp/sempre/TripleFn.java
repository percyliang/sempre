package edu.stanford.nlp.sempre;

import edu.stanford.nlp.sempre.*;
import java.util.*;
import fig.basic.LispTree;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Takes two strings and returns triple or partial triple build from them
 *
 * @author emlozin
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
        string1 = org.apache.commons.lang.StringEscapeUtils.unescapeJava(string1);
        string2 = org.apache.commons.lang.StringEscapeUtils.unescapeJava(string2);
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
        else if (this.mode.equals("tp")) {
            triple.put("type", string1);
            triple.put("predicate", string2);
        }
        else if (this.mode.equals("pt")) {
            triple.put("predicate", string1);
            triple.put("type", string2);
        }
        else if (this.mode.equals("to")) {
            triple.put("type", string1);
            triple.put("object", string2);
        }
        else if (this.mode.equals("ot")) {
            triple.put("object", string1);
            triple.put("type", string2);
        }

        // Convert a Map into JSON string.
        Gson gson = new Gson();
        String str_triple = gson.toJson(triple);
        //System.out.println("Init"+str_triple);
        return str_triple;
    }

    public String merge_formula(String string1, String string2) {
        // Convert JSON string back to Map.
        string1 = org.apache.commons.lang.StringEscapeUtils.unescapeJava(string1);
        string2 = org.apache.commons.lang.StringEscapeUtils.unescapeJava(string2);
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> triple = gson.fromJson(string1, type);

        // Convert JSON string back to Map.
        Map<String, String> triple2 = gson.fromJson(string2, type);

        Map<String, String> result = new HashMap<String, String>();

        result.putAll(triple);
        for (Map.Entry<String, String> entry : triple2.entrySet())
        {
            if(result.containsKey(entry.getKey()) && !(entry.getValue().equals(result.get(entry.getKey()))))
                result.put(entry.getKey(),result.get(entry.getKey()).concat(","+entry.getValue()));
            else
                result.put(entry.getKey(),entry.getValue());
        }
        // Convert a Map into JSON string.

        if (result.containsKey("subject") && result.containsKey("predicate") && result.containsKey("object")){
            return final_string(result);
        }
        String str_triple = gson.toJson(result);
        //System.out.println("Merge"+str_triple);
        return str_triple;
    }

    public String concat_formula(String string1, String string2) {
        // Convert JSON string back to Map.
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> triple = new HashMap();
        string1 = org.apache.commons.lang.StringEscapeUtils.unescapeJava(string1);
        string2 = org.apache.commons.lang.StringEscapeUtils.unescapeJava(string2);
        if (string1.contains("{")){
            triple = gson.fromJson(string1, type);
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
        }
        if (string2.contains("{")){
            triple = gson.fromJson(string2, type);
            if (this.mode.equals("spo")) {
                triple.put("subject", string1);
            }
            else if (this.mode.equals("sop")) {
                triple.put("subject", string1);
            }
            else if (this.mode.equals("pso")) {
                triple.put("predicate", string1);
            }
            else if (this.mode.equals("pos")) {
                triple.put("predicate", string1);
            }
            else if (this.mode.equals("osp")) {
                triple.put("object", string1);
            }
            else if (this.mode.equals("ops")) {
                triple.put("object", string1);
            }
        }

        if (triple.containsKey("subject") && triple.containsKey("predicate") && triple.containsKey("object")){
            return final_string(triple);
        }
        String str_triple = gson.toJson(triple);
        //System.out.println("Concat"+str_triple);
        return str_triple;
    }

    public String final_string(Map<String, String> triple){
        String[] subject = split(triple.get("subject").toString());
        String[] predicate = split(triple.get("predicate").toString());
        String[] object = split(triple.get("object").toString());
        StringBuilder out = new StringBuilder();
//        if (triple.get("subject").toString().contains(";")||triple.get("subject").toString().contains(","))
//            out.append("|");
/*        if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
            out.append("(");
        if (triple.get("object").toString().contains(";")||triple.get("object").toString().contains(","))
            out.append("(");*/
        for (String s : subject){
            if (out.toString().length() > 1 && triple.get("subject").toString().contains(","))
                out.append("&");
            if (out.toString().length() > 1 && triple.get("subject").toString().contains(";"))
                out.append(";");
            if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
                out.append("{");
            StringBuilder out2 = new StringBuilder();
            for (String p : predicate) {
                if (out2.toString().length() > 0 && triple.get("predicate").toString().contains(","))
                    out2.append("&");
                if (out2.toString().length() > 0 && triple.get("predicate").toString().contains(";"))
                    out2.append(";");
                if (triple.get("object").toString().contains(";")||triple.get("object").toString().contains(","))
                    out2.append("[");
                StringBuilder out3 = new StringBuilder();
                for (String o : object) {
                    if (out3.toString().length() > 0 && triple.get("object").toString().contains(","))
                        out3.append("&");
                    if (out3.toString().length() > 0 && triple.get("object").toString().contains(";"))
                        out3.append(";");
                    out3.append("(");
                    out3.append(s);
                    out3.append(",");
                    out3.append(p);
                    out3.append(",");
                    out3.append(o);
                    out3.append(")");
                    if (triple.containsKey("type")){
                        out3.append("&");
                        out3.append("(");
                        out3.append(o);
                        out3.append(",has_type,");
                        out3.append(triple.get("type"));
                        out3.append(")");
                    }
                }
                out2.append(out3.toString());
                if (triple.get("object").toString().contains(";")||triple.get("object").toString().contains(","))
                    out2.append("]");
            }
            out.append(out2.toString());
            if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
                out.append("}");
        }
//        if (triple.get("subject").toString().contains(";")||triple.get("subject").toString().contains(","))
//            out.append("|");
/*        if (triple.get("predicate").toString().contains(";")||triple.get("predicate").toString().contains(","))
            out.append(")");
        if (triple.get("object").toString().contains(";")||triple.get("object").toString().contains(","))
            out.append(")");   */
        //System.out.println(out.toString());
        return out.toString();
    }

    public String final_formula(String string1, String string2) {
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
        if(!triple.containsKey("subject"))
            return concat_formula(string1,string2);
        if(!triple.containsKey("predicate"))
            return concat_formula(string1,string2);
        if(!triple.containsKey("object"))
            return concat_formula(string1,string2);
        return final_string(triple);
    }

    public DerivationStream call(Example ex, final Callable c) {
        return new SingleDerivationStream() {
            @Override
            public Derivation createDerivation() {
                String out = new String();
                if (c.childStringValue(0) == null || c.childStringValue(1) == null)
                    return null;
                // Do not accept reverse values
                if (c.childStringValue(0).contains("!") || c.childStringValue(1).contains("!"))
                    return null;
                if (c.childStringValue(0).contains("{") && c.childStringValue(1).contains("{")){
                    out = merge_formula(c.childStringValue(0),c.childStringValue(1));
                }
                else if (c.childStringValue(0).contains("{")){
                    out = final_formula(c.childStringValue(0),c.childStringValue(1));
                    return new Derivation.Builder()
                            .withCallable(c)
                            .withTripleFormulaFrom(out)
                            .createDerivation();
                }
                else if (c.childStringValue(1).contains("{")){
                    out = concat_formula(c.childStringValue(0),c.childStringValue(1));
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
        str = str.replaceAll("\\(","");
        str = str.replaceAll("\\)","");
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
