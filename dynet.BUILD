package(default_visibility = ["//visibility:public"])

cc_library(
    name = "dynet-lib",
    srcs = glob(["dynet/*.cc"], exclude=["dynet/cuda.cc"]),
    hdrs = glob(["dynet/*.h"]),
    linkopts = ["-lm", "-lboost_serialization", "-lboost_system", "-lboost_filesystem"],
    deps = ["@easyloggingpp//:easyloggingpp-lib",
            "@eigen//:eigen-lib"]
)
