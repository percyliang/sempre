package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.LogInfo;

import java.util.*;

/**
 * Represents a triple value.
 * @author emlozin
 **/
public class TripleValue extends Value {
    public List<Triple> triples;
    public List<TripleValue> children;
    public enum TripleType{
        OR,AND
    };
    public TripleType type;


    public class Triple{
        public final String subject;
        public final String predicate;
        public final String object;

        public Triple(String str) {
            //System.out.println("String triple: "+str);
            if (str.startsWith("(("))
                str = str.substring(2,str.indexOf(")",2));
            if (str.startsWith("("))
                str = str.substring(1,str.indexOf(")",2));
            String[] t = new String[0];
            if (str.contains(","))
                t = str.split(",");
            if (t.length==3) {
                this.subject = t[0];
                this.predicate = t[1];
                this.object = t[2];
            }
            else{
                this.subject = null;
                this.predicate = null;
                this.object = null;
            }
        }

        public Triple(LispTree tree) {
            this.subject = tree.child(1).value;
            this.predicate = tree.child(2).value;
            this.object = tree.child(3).value;
        }

        public LispTree toLispTree() {
            LispTree tree = LispTree.proto.newList();
            tree.addChild("triple");
            tree.addChild(subject);
            tree.addChild(predicate);
            tree.addChild(object);
            return tree;
        }

        public String sortString() {
            return ("\"(" + subject + ","
                    + predicate + ","
                    + object + ")\""); }

        public String pureString() {
            return ("(" + subject + ","
                    + predicate + ","
                    + object + ")");
        }

        public int hashCode() {
            return (subject.hashCode() +
                    predicate.hashCode() +
                    object.hashCode()); }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Triple that = (Triple) o;
            return (this.subject.equals(that.subject) &&
                    this.predicate.equals(that.predicate) &&
                    this.object.equals(that.object));
        }
    }

    public TripleValue(String str) {
        this.triples = new ArrayList();
        this.children = new ArrayList();
        this.type = TripleType.AND;
        if (str.contains(";{"))
            this.type = TripleType.OR;
        else if (str.contains(";["))
            this.type = TripleType.OR;
        if (str.contains("{")) {
            //System.out.println("Predicates:"+str);
            str = str.substring(str.indexOf("{")+1);
            //System.out.println("Predicates aft:"+str);
            String[] candidates = str.split("}");
            for (String c : candidates) {
                //System.out.println("Candidate:"+c);
                if (c.contains(";")||c.contains("&")) {
                    if (candidates.length > 1)
                        this.children.add(new TripleValue(c));
                    else {
                        TripleValue temp = (new TripleValue(c));
                        this.triples = temp.triples;
                        this.children = temp.children;
                        this.type = temp.type;
                    }
                }
                else
                    this.triples.add(new Triple(c));
            }
        }
        else if (str.contains("[")) {
            //System.out.println("Objects:"+str);
            str = str.substring(str.indexOf("[")+1);
            String[] candidates = str.split("]");
            //System.out.println("Objects aft:"+str);
            for (String c : candidates) {
                //System.out.println("Candidate:"+c);
                if (c.contains(";")||c.contains("&")) {
                    if (candidates.length > 1)
                        this.children.add(new TripleValue(c));
                    else {
                        TripleValue temp = (new TripleValue(c));
                        this.triples = temp.triples;
                        this.children = temp.children;
                        this.type = temp.type;
                    }
                }
                else
                    this.triples.add(new Triple(c));
            }
        }
        else if (str.contains(";")) {
            String[] candidates = str.split(";");
            //for (String c : candidates)
            //    System.out.println("Single: "+c);
            for (String c : candidates) {
                this.triples.add(new Triple(c));
            }
            this.type = TripleType.OR;
        }
        else if (str.contains("&")) {
            String[] candidates = str.split("&");
            //for (String c : candidates)
            //    System.out.println("Single: "+c);
            for (String c : candidates) {
                this.triples.add(new Triple(c));
            }
            this.type = TripleType.AND;
        }
        else{
            String[] candidates = str.split("&");
            this.triples.add(new Triple(str));
        }
    }

    public TripleValue(LispTree tree) {
        this.triples = new ArrayList();
        this.children = new ArrayList();
        for (int i = 1; i < tree.children.size(); i++) {
            if (tree.child(i).value != null)
            {
                if (tree.child(i).value.toString().equals("or"))
                {
                    this.type = TripleType.OR;
                }
            }
            else
            {
                if (tree.child(i).toString().contains("triples "))
                {
                    this.children.add(new TripleValue(tree.child(i)));
                }
                else if (tree.child(i).toString().contains("triple "))
                {
                    this.triples.add(new Triple(tree.child(i)));
                }
            }
        }
    }

    public LispTree toLispTree() {
        LispTree tree = LispTree.proto.newList();
        tree.addChild("triples");
        if (this.type == TripleType.OR)
            tree.addChild("or");
        for(Triple triple : this.triples) {
            tree.addChild(triple.toLispTree());
        }
        for(TripleValue triple : this.children) {
            tree.addChild(triple.toLispTree());
        }
        return tree;
    }

    @Override public String sortString() {
        StringBuilder out = new StringBuilder();
        StringBuilder out2 = new StringBuilder();
        if (this.triples.size() > 1)
            out.append("(");
        for(Triple triple : this.triples) {
            if (out.toString().length()>0 && this.type == TripleType.AND)
                out.append(",");
            if (out.toString().length()>0 && this.type == TripleType.OR)
                out.append(";");
            out.append(triple.sortString());
        }
        if (this.triples.size() > 1)
            out.append(")");
        if (this.children.size() > 1)
            out2.append("(");
        for(TripleValue triple : this.children) {
            if (out.toString().length()>0 && triple.type == TripleType.AND)
                out2.append(",");
            if (out.toString().length()>0 && triple.type == TripleType.OR)
                out2.append(";");
            out2.append(triple.sortString());
        }
        if (this.children.size() > 1)
            out2.append(")");
        out.append(out2.toString());
        return out.toString();
    }

    @Override public String pureString() {
        StringBuilder out = new StringBuilder();
        StringBuilder out2 = new StringBuilder();
        out.append("\"");
        if (this.triples.size() > 1)
            out.append("(");
        for(Triple triple : this.triples) {
            if (out.toString().length()>1 && this.type == TripleType.AND)
                out.append(",");
            if (out.toString().length()>1 && this.type == TripleType.OR)
                out.append(";");
            out.append(triple.pureString());
        }
        if (this.triples.size() > 1)
            out.append(")");
        out.append("\" \"");
        if (this.children.size() > 1)
            out2.append("(");
        for(TripleValue triple : this.children) {
            if (out2.toString().length()>0 && triple.type == TripleType.AND)
                out2.append(",");
            if (out2.toString().length()>0 && triple.type == TripleType.OR)
                out2.append(";");
            out2.append(triple.pureString());
        }
        if (this.children.size() > 1)
            out2.append(")");
        out.append(out2.toString());
        out.append("\"");
        return out.toString();
    }

    @Override public int hashCode() {
        int code = 0;
        if (this.triples != null) {
            for (Triple triple : this.triples) {
                code += triple.hashCode();
            }
        }
        if (this.children != null) {
            for (TripleValue triple : this.children) {
                code += triple.hashCode();
            }
        }
        return code;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TripleValue that = (TripleValue) o;
        if (this.triples.size() != that.triples.size())
            return false;
        for(int i = 0; i < this.triples.size(); i++) {
            if (!this.triples.get(i).equals(that.triples.get(i)))
                return false;
        }
        if (this.children.size() != that.children.size())
            return false;
        for(int i = 0; i < this.children.size(); i++) {
            if (!this.children.get(i).equals(that.children.get(i)))
                return false;
        }
        return true;
    }
}

