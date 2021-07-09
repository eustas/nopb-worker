package ru.eustas.nopbworker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ReaderFuzzer {
  public static void fuzzerTestOneInput(byte[] data) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      Hub hub = new Hub(bais, baos);
      while (true) {
        WorkRequest item = hub.readRequest();
        if (item == null) break;
      }
    } catch (IOException ex) {
      if (!"Corrupted input stream".equals(ex.getMessage())) {
        throw ex;
      }
    }
  }
}
