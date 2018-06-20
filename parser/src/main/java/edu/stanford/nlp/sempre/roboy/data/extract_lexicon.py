#!/usr/bin/python
import glob, os

os.chdir("./dbpedia")
output = open("people.lexicon", "w")
add_prefix = False

written = 0
unwritten = 0
for file in glob.glob("*.ttl"):
    input = open(file, "r")
    prefix = {}
    for line in input:
        if line.find("<http://xmlns.com/foaf/0.1/name>") >= 0:
            written += 1
            lexeme = "{\"lexeme\": \"" + line[line.index("\"")+1:line.index("@")-1]  + "\", "
            lexeme += "\"formula\": \"" + line[line.index("<http://dbpedia.org/resource/"):line.index(">")] + "\", "
            lexeme = lexeme.replace("<http://dbpedia.org/resource/","resource:")
            lexeme += "\"type\": \"NamedEntity\"}"
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
