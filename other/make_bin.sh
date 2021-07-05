#!/bin/bash

cat work-request-00.pbtxt | protoc --encode=blaze.worker.WorkRequest worker_protocol.proto > work-request-00.pb
cat work-request-01.pbtxt | protoc --encode=blaze.worker.WorkRequest worker_protocol.proto > work-request-01.pb
cat work-request-02.pbtxt | protoc --encode=blaze.worker.WorkRequest worker_protocol.proto > work-request-02.pb
python make_bundle.py ../test/work-request.bin work-request-00.pb work-request-01.pb work-request-02.pb
rm work-request-*.pb

cat work-response-00.pbtxt | protoc --encode=blaze.worker.WorkResponse worker_protocol.proto > work-response-00.pb
cat work-response-01.pbtxt | protoc --encode=blaze.worker.WorkResponse worker_protocol.proto > work-response-01.pb
python make_bundle.py ../test/work-response.bin work-response-00.pb work-response-01.pb
rm work-response-*.pb
