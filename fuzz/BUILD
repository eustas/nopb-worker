load("@rules_fuzzing//fuzzing:java_defs.bzl", "java_fuzz_test")

java_fuzz_test(
    name = "ReaderFuzzer",
    srcs = ["ReaderFuzzer.java"],
    deps = ["@main://main:nopb-worker"],
    target_class = "ru.eustas.nopbworker.ReaderFuzzer",
)
