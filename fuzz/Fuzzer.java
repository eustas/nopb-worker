package ru.eustas.nopbworker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Fuzzer {
  public static void fuzzerTestOneInput(byte[] data) throws IOException {
    if (data.length == 0)
      return;
    byte last = data[data.length - 1];
    if ((last & 1) == 0) {
      fuzzReader(new ByteArrayInputStream(data, 0, data.length - 1));
    } else {
      fuzzWriter(ByteBuffer.wrap(data, 0, data.length - 1));
    }
  }

  static void fuzzReader(InputStream input) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      Hub hub = new Hub(input, baos);
      while (true) {
        WorkRequest item = hub.readRequest();
        if (item == null)
          break;
      }
    } catch (IOException ex) {
      if (!"Corrupted input stream".equals(ex.getMessage())) {
        throw ex;
      }
    }
  }

  static void fuzzWriter(ByteBuffer input) throws IOException {
    WorkResponse item = new WorkResponse();
    try {
      item.exitCode = input.getInt();
      item.requestId = input.getInt();
      item.wasCancelled = ((input.get() & 1) != 0);
      byte[] tail = new byte[input.remaining()];
      input.get(tail);
      item.output = new String(tail, StandardCharsets.UTF_8);
    } catch (BufferUnderflowException ex) {
      // Okay, input is too short.
      return;
    }
    ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Hub hub = new Hub(bais, baos);
    hub.writeResponse(item);
  }
}
