package ru.eustas.nopbworker;

import static org.junit.Assert.assertArrayEquals;

import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class WriterTest {
  byte[] readFile(String name) throws IOException {
    return Files.readAllBytes(Path.of("test", name));
  }

  WorkResponse readJson(String name) throws IOException, JsonbException {
    return JsonbBuilder.create().fromJson(
        new ByteArrayInputStream(readFile(name)), WorkResponse.class);
  }

  @Test
  public void testWrite() throws IOException, JsonbException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Hub hub = new Hub(new ByteArrayInputStream(new byte[0]), baos);
    hub.writeResponse(readJson("work-response-00.json"));
    hub.writeResponse(readJson("work-response-01.json"));

    byte[] expected = readFile("work-response.bin");
    byte[] actual = baos.toByteArray();
    
    for (int j = 0; j < actual.length; j++) {
      System.out.format("%02X ", actual[j]);
    }
    System.out.println();

    assertArrayEquals(expected, actual);
  }
}
