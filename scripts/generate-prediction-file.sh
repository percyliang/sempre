#!/bin/sh
#Generate predictions file for evaluation script 
#arguments: (1) execution numebr (2) iteration number (3) final output file

fig/bin/tab e/$1.exec/learner.events iter group utterance targetValue predValue | grep -P "$2\ttest" | cut -f3,4,5 > pred_temp
java -cp "libsempre/*:lib/*" edu.stanford.nlp.sempre.freebase.utils.FileUtils pred_temp $3
rm pred_temp
