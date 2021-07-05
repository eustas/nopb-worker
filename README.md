### Introduction

This small (8KiB) standalone (i.e. without dependencies) library provides a lightweight
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

Simple use example would be:
```java
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
