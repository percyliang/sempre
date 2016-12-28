#include <jni.h>
#include <stdio.h>
#include "edu_stanford_nlp_sempre_ComputationGraphWrapper.h"
 
// Implementation of native method sayHello() of HelloJNI class
JNIEXPORT jdouble JNICALL Java_edu_stanford_nlp_sempre_ComputationGraphWrapper_test(JNIEnv *env, jobject thisObj) {
   printf("Hello World!\n");
   return -1.0;
}
