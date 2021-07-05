package ru.eustas.nopbworker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import java.nio.file.Path;
import org.junit.Test;

public class ReaderTest {
  byte[] readFile(String name) throws IOException {
    return Files.readAllBytes(Path.of("test", name));
  }

  WorkRequest readJson(String name) throws IOException, JsonbException {
    return JsonbBuilder.create().fromJson(
        new ByteArrayInputStream(readFile(name)), WorkRequest.class);
  }

  @Test
  public void testRead() throws IOException, JsonbException {
    byte[] data = readFile("work-request.bin");
    Hub hub = new Hub(new ByteArrayInputStream(data), new ByteArrayOutputStream());
    WorkRequest item;

    item = hub.readRequest();
    assertEquals(readJson("work-request-00.json"), item);

    item = hub.readRequest();
    assertEquals(readJson("work-request-01.json"), item);

    item = hub.readRequest();
    assertEquals(readJson("work-request-02.json"), item);

    item = hub.readRequest();
    assertEquals(null, item);
  }
}
