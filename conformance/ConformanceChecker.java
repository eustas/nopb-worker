package ru.eustas.nopbworker;

import com.google.devtools.build.lib.worker.WorkerProtocol;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public class ConformanceChecker {
  public static void fuzzerTestOneInput(byte[] data) throws IOException {
    if (data.length == 0)
      return;
    byte flags = data[0];
    if ((flags & 1) == 0) {
      checkRequest(ByteBuffer.wrap(data, 1, data.length - 1));
    } else {
      checkResponse(ByteBuffer.wrap(data, 1, data.length - 1));
    }
  }

  static int readNumber(ByteBuffer input, int maxBits) {
    int bits = input.getShort() % maxBits;
    if (bits == 0) return 0;
    int result = input.getInt();
    result |= 0x80000000;
    return result >>> (32 - bits);
  }

  static byte[] makeBlob(Random rng, ByteBuffer input, int maxBits) {
    int length = readNumber(input, maxBits);
    byte[] result = new byte[length];
    rng.nextBytes(result);
    return result;
  }

  static void checkRequest(ByteBuffer input) throws IOException {
    WorkerProtocol.WorkRequest.Builder origBuilder = WorkerProtocol.WorkRequest.newBuilder();
    try {
      Random rng = new Random(input.getLong());
      origBuilder.setRequestId(input.getInt());
      origBuilder.setCancel((input.get() & 1) != 0);
      int numArguments = readNumber(input, 8);
      for (int i = 0; i < numArguments; ++i) {
        origBuilder.addArguments(new String(makeBlob(rng, input, 16), StandardCharsets.UTF_8));
      }
      int numInputs = readNumber(input, 10);
      for (int i = 0; i < numInputs; ++i) {
        WorkerProtocol.Input.Builder inputBuilder = WorkerProtocol.Input.newBuilder();
        inputBuilder.setPath(new String(makeBlob(rng, input, 12), StandardCharsets.UTF_8));
        inputBuilder.setDigest(com.google.protobuf.ByteString.copyFrom(makeBlob(rng, input, 8)));
        origBuilder.addInputs(inputBuilder);
      }
    } catch (BufferUnderflowException ex) {
      // Okay, input is too short.
      return;
    }
    WorkerProtocol.WorkRequest orig = origBuilder.build();

    ByteArrayOutputStream serialized = new ByteArrayOutputStream();
    orig.writeDelimitedTo(serialized);

    ByteArrayInputStream bais = new ByteArrayInputStream(serialized.toByteArray());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    WorkRequest deserialized = null;
    try {
      Hub hub = new Hub(bais, baos);
      deserialized = hub.readRequest();
    } catch (IOException ex) {
      if (!"Corrupted input stream".equals(ex.getMessage())) {
        throw ex;
      }
    }
    if (bais.available() != 0) {
      throw new RuntimeException("Stream not consumed completely");
    }

    if (deserialized == null) {
      throw new RuntimeException("Failed to deserialize");
    }

    if ((deserialized.requestId != orig.getRequestId()) ||
        (deserialized.cancel != orig.getCancel()) ||
        !Arrays.deepEquals(deserialized.arguments.toArray(), orig.getArgumentsList().toArray())) {
      throw new RuntimeException("Deserilized object does not match original one");
    }

    if (deserialized.inputs.size() != orig.getInputsList().size()) {
      throw new RuntimeException("Deserilized object does not match original one");
    }
    {
      int numInputs = deserialized.inputs.size();
      for (int i = 0; i < numInputs; ++i) {
        WorkRequest.Input deserializedInput = deserialized.inputs.get(i);
        WorkerProtocol.Input origInput = orig.getInputsList().get(i);
        if (!Objects.equals(deserializedInput.path, origInput.getPath()) ||
            !Objects.deepEquals(deserializedInput.digest, origInput.getDigest().toByteArray())) {
          throw new RuntimeException("Deserilized object does not match original one");
        }
      }
    }
  }

  static void checkResponse(ByteBuffer input) throws IOException {
    WorkResponse orig = new WorkResponse();
    try {
      Random rng = new Random(input.getLong());
      orig.exitCode = input.getInt();
      orig.requestId = input.getInt();
      orig.wasCancelled = ((input.get() & 1) != 0);
      orig.output = new String(makeBlob(rng, input, 23), StandardCharsets.UTF_8);
    } catch (BufferUnderflowException ex) {
      // Okay, input is too short.
      return;
    }
    ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Hub hub = new Hub(bais, baos);
    hub.writeResponse(orig);

    ByteArrayInputStream serialized = new ByteArrayInputStream(baos.toByteArray());
    WorkerProtocol.WorkResponse deserialized = WorkerProtocol.WorkResponse.parseDelimitedFrom(serialized);
    if (serialized.available() != 0) {
      throw new RuntimeException("Excess output");
    }

    if ((orig.exitCode != deserialized.getExitCode()) ||
        !Objects.equals(orig.output, deserialized.getOutput()) ||
        (orig.requestId != deserialized.getRequestId()) ||
        (orig.wasCancelled != deserialized.getWasCancelled())) {
      throw new RuntimeException("Deserilized object does not match original one");
    }
  }
}
