### Introduction

This small (under 8KiB) standalone (i.e. without dependencies) library provides a lightweight
alternative to using
[Protocol Buffers](https://developers.google.com/protocol-buffers) for
[Bazel Persistent Workers](https://docs.bazel.build/versions/main/persistent-workers.html).

Unlike
["JSON support for persistent workers"](https://blog.bazel.build/2020/11/11/json-workers.html)
this solution does not require users to specify additional
"--experimental_worker_allow_json_protocol" to Bazel.

This is not a drop-in replacement for protobuf solution: `WorkRequest` and `WorkResponse` are
placed in different package and are POJOs (plain-old-Java-objects). Thus there are no builders,
"has*", and other field methods. All fields are mutable.

### Usage

1. Put the following snippet to `WORKSPACE` file:
```
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_jar")

http_jar (
    name = "nopb-worker",
    url = "https://github.com/eustas/nopb-worker/releases/download/v0.9.0/nopb-worker.jar",
    sha256 = "5f51edbe43e16d38acec7a0dc1ccd7eab2f59e65205b69153b7b8b94a4393daf",
)
```

2. Add dependency in `BUILD` file:
```
 deps = [
   ...
   "@nopb-worker//jar",
   ...
 ]
```

3. Create and use "Hub" in code, e.g.:
```java
import ru.eustas.nopbworker.Hub;
import ru.eustas.nopbworker.WorkRequest;
import ru.eustas.nopbworker.WorkResponse;
  ...
  Hub hub = new Hub(System.in, System.out);
  ...
  while (true) {
    WorkRequest task = hub.readRequest();
    if (task == null) {
      break;
    }
    WorkResponse result = new WorkResponse();
    result.requestId = task.requestId;
    ...
    hub.writeResponse(result);
  }
```
