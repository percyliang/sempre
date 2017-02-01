#ifndef MODEL_HOLDER_H_
#define MODEL_HOLDER_H_

#include "dynet/dynet.h"
#include "dynet/expr.h"
#include "dynet/nodes.h"
#include "dynet/training.h"

using namespace dynet;
using namespace dynet::expr;
using namespace std;

namespace nnsp {
  class ModelHolder{
    public:
      void Init(unsigned length);
      Model _mod;
      Parameter _params;
      Trainer* _sgd;
  };
} // namespace nnsp

#endif 
