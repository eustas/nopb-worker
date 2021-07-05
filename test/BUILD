java_test(
    name = "ReaderTest",
    srcs = ["ReaderTest.java"],
    data = [
        "work-request.bin",
        "work-request-00.json",
        "work-request-01.json",
        "work-request-02.json",
    ],
    test_class = "ru.eustas.nopbworker.ReaderTest",  # ridiculous
    deps = [
        "//main:nopbworker",
        "@jakarta_json_bind_api//jar",
        "@junit4//jar",
        "@org_eclipse_yasson//jar",
        "@org_glassfish_jakarta_json//jar",
    ],
)

java_test(
    name = "WriterTest",
    srcs = ["WriterTest.java"],
    test_class = "ru.eustas.nopbworker.WriterTest",  # ridiculous
    data = [
        "work-response.bin",
        "work-response-00.json",
        "work-response-01.json",
    ],
    deps = [
        "//main:nopbworker",
        "@jakarta_json_bind_api//jar",
        "@junit4//jar",
        "@org_eclipse_yasson//jar",
        "@org_glassfish_jakarta_json//jar",
    ],
)
