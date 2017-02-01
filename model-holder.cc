#include "model-holder.h"

namespace nnsp {

void ModelHolder::Init(unsigned length) {
  _params = _mod.add_parameters({1, length});
  _sgd = new AdagradTrainer(_mod);
}

} // namespace nnsp
