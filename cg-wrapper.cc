// Compile:
// bazel build -c opt cg-wrapper.so
// java -ea -Dmodules=core -Djava.library.path=. -cp libsempre/*:lib/* edu.stanford.nlp.sempre.ComputationGraphWrapper
#include <jni.h>
#include <stdio.h>
#include "dynet/dynet.h"
#include "dynet/nodes.h"
#include "dynet/expr.h"
#include "edu_stanford_nlp_sempre_ComputationGraphWrapper.h"
#include "edu_stanford_nlp_sempre_ComputationGraphWrapper_Options.h"
#include "model-holder.h"

using namespace std;
using namespace dynet;
using namespace dynet::expr;

std::unique_ptr<nnsp::ModelHolder> model_holder(new nnsp::ModelHolder());
bool verbose = false;
 
JNIEXPORT void JNICALL Java_edu_stanford_nlp_sempre_ComputationGraphWrapper_InitDynet(JNIEnv *env,
    jobject this_obj, jsize num_params) {
  int argc = 1;
  char *args[] = {nullptr};
  char **argv = static_cast<char**>(args);
  dynet::initialize(argc, argv);
  model_holder->Init(num_params);
}

JNIEXPORT jdouble JNICALL Java_edu_stanford_nlp_sempre_ComputationGraphWrapper_scoreWithNetwork(JNIEnv *env, jobject this_obj, jdoubleArray java_input) {

  // Convert to C-types.
  jdouble *c_input = env->GetDoubleArrayElements(java_input, NULL);
  const unsigned length = static_cast<unsigned>(env->GetArrayLength(java_input));

  // Declare model, parameters and graph.
  ComputationGraph cg;
  Expression W = parameter(cg, model_holder->_params);

  // Define input and output.
  vector<dynet::real> nn_input_values(length);
  for (int i = 0; i < length; ++i) {
    nn_input_values[i] = c_input[i];
  }
  Expression nn_input = input(cg, {length}, &nn_input_values);
  Expression dot_prod = W * nn_input;

  // Run graph and return score.
  dynet::real score = as_scalar(cg.forward(dot_prod));
  env->ReleaseDoubleArrayElements(java_input, c_input, 0);
  return score;
}

JNIEXPORT void JNICALL Java_edu_stanford_nlp_sempre_ComputationGraphWrapper_computeCondLikelihoodLoss
  (JNIEnv *env, jobject this_obj, jdoubleArray java_features, jdoubleArray java_rewards) {
  jdouble *c_features = env->GetDoubleArrayElements(java_features, NULL);
  const unsigned features_length = static_cast<unsigned>(env->GetArrayLength(java_features));
  if (features_length == 0) return;
  jdouble *c_rewards = env->GetDoubleArrayElements(java_rewards, NULL);
  const unsigned rewards_length = static_cast<unsigned>(env->GetArrayLength(java_rewards));

  unsigned num_features = features_length / rewards_length;

  ComputationGraph cg;
  Expression W = parameter(cg, model_holder->_params);

  // Build features.
  assert (c_features.length == features_length);
  vector<dynet::real> nn_feature_values(features_length);
  for (int i = 0; i < features_length; ++i) {
    nn_feature_values[i] = c_features[i];
  }
  Expression nn_features = input(cg, {num_features, rewards_length}, &nn_feature_values);

  // Build rewards.
  vector<dynet::real> nn_rewards_values(rewards_length);
  assert (c_rewards.length == rewards_length);
  for (int i = 0; i < rewards_length; ++i) {
    nn_rewards_values[i] = c_rewards[i];
  }
  Expression nn_rewards = input(cg, {rewards_length}, &nn_rewards_values);

  // Build graph.
  Expression logits = W * nn_features; 
  Expression probs = softmax(transpose(logits));
  Expression weighted_reward = cmult(probs, nn_rewards);
  if (verbose) {
    vector<dynet::real> logits_vals = as_vector(cg.forward(logits));
    vector<dynet::real> rewards_vals = as_vector(cg.forward(nn_rewards));
    printf("logits: ");
    for (auto logits_val: logits_vals) {
      printf("%f ", logits_val);
    }
    printf("\n");
  }
  Expression loss = -sum_cols(transpose(weighted_reward));

  // Run graph and update params. 
  dynet::real l = as_scalar(cg.forward(loss));
  printf("Example loss=%f", l);
  cg.backward(loss);
  model_holder->_sgd->update(1.0);

  env->ReleaseDoubleArrayElements(java_features, c_features, 0);
  env->ReleaseDoubleArrayElements(java_rewards, c_rewards, 0);
}
