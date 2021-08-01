load("@rules_fuzzing//fuzzing:java_defs.bzl", "java_fuzz_test")

java_fuzz_test(
    name = "Fuzzer",
    srcs = ["Fuzzer.java"],
    deps = ["@main//main:nopb-worker"],
    target_class = "ru.eustas.nopbworker.Fuzzer",  # ridiculous
)
