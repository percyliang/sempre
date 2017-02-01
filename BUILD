package(default_visibility = ["//visibility:public"])

cc_library(
    name = "jni",
    hdrs = glob(["java_home/include/**/*.h"]),
    includes = [
        "java_home/include",
        "java_home/include/Darwin",
    ],
)

cc_library(
    name = "model-holder",
    srcs = ["model-holder.cc"],
    hdrs = ["model-holder.h"],
    deps = [
        "@dynet//:dynet-lib",
    ],
)

cc_binary(
    name = "cg-wrapper.so",
    srcs = ["cg-wrapper.cc",
            "edu_stanford_nlp_sempre_ComputationGraphWrapper_Options.h",
            "edu_stanford_nlp_sempre_ComputationGraphWrapper.h",
            ],
    deps = [
        "jni",
        "model-holder",
        "@dynet//:dynet-lib",
    ],
    linkshared = 1,
)
