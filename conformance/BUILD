load("@rules_fuzzing//fuzzing:java_defs.bzl", "java_fuzz_test")
load("@rules_proto//proto:defs.bzl", "proto_library")

proto_library(
    name = "worker_protocol",
    srcs = ["worker_protocol.proto"],
)

java_proto_library(
    name = "worker_protocol_java_proto",
    deps = [":worker_protocol"],
)

# Fuzzer machinery is used for intelligent corner cases discovery.
java_fuzz_test(
    name = "ConformanceChecker",
    srcs = ["ConformanceChecker.java"],
    deps = [
        "@main//main:nopb-worker",
        "@com_google_protobuf//:protobuf_java",
        ":worker_protocol_java_proto",
    ],
    target_class = "ru.eustas.nopbworker.ConformanceChecker",  # ridiculous
)
