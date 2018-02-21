#!/usr/bin/python
import glob, os

os.chdir("./dbpedia")
output = open("people.examples", "w")
add_prefix = False

written = 0
unwritten = 0
for file in glob.glob("*.ttl"):
    input = open(file, "r")
    prefix = {}
    for line in input:
        if line.find("<http://xmlns.com/foaf/0.1/name>") >= 0:
            written += 1
            lexeme = "(example\n\t"
            lexeme += "(utterance \"" + line[line.index("\"")+1:line.index("@")-1]  + "\")\n\t"
            lexeme += "(targetValue (name " + line[line.index("<http://dbpedia.org/resource/"):line.index(">")] + " \"" + line[line.index("\"")+1:line.index("@")-1].replace(" ","\ ") + "\"))\n)\n"
            lexeme = lexeme.replace("<http://dbpedia.org/resource/","resource:")
            # print(str(lexeme))
            if not written%10000:
                print "Written: ",written
            output.write(lexeme+"\n")
        #print lexeme
        else:
            unwritten += 1
            continue
print "Written records:", written
print "Total records:", written + unwritten
