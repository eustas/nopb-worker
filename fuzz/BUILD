load("@rules_fuzzing//fuzzing:java_defs.bzl", "java_fuzz_test")

java_fuzz_test(
    name = "ReaderFuzzer",
    srcs = ["ReaderFuzzer.java"],
    deps = ["@main//main:nopb-worker"],
    target_class = "ru.eustas.nopbworker.ReaderFuzzer",
)

java_test(
    name = "TrophiesTest",
    srcs = [
        "ReaderFuzzer.java",
        "TrophiesTest.java"
    ],
    test_class = "ru.eustas.nopbworker.TrophiesTest",  # ridiculous
    deps = [
        "@main//main:nopb-worker",
        "@junit4//jar",
    ],
)
