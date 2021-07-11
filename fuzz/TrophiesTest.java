package ru.eustas.nopbworker;

import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.util.Base64;
import org.junit.Test;

public class TrophiesTest {
  private static void check(String base64) throws IOException {
    ReaderFuzzer.fuzzerTestOneInput(Base64.getDecoder().decode(base64));
  }

  @Test
  public void testMessageLength() throws IOException {
    check("x8fHx8fHCgo=");
  }

  @Test
  public void testNegativeLength() throws IOException {
    check("AP8A/QAAAAAAAAEAAAAAAAAAAAAAAAAAGBgMEmAYGBgYGAAYBhICGBgYPRgABQoABQoKluzs7Oz/////AQUAAAAq"
        + "AAAAAAAAAAAAABgYAhJgGBgYGBgAGAYwMDAwMDAwSDAwAAAYGAwSYBgYGBgYABgGEgIYGBg9GAAFCgAFCgEFAAAA"
        + "AAUKGAAAChgYGA==");
  }
}