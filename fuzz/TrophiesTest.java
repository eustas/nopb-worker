package ru.eustas.nopbworker;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import org.junit.Test;

public class TrophiesTest {
  private static void checkReader(String base64) throws IOException {
    InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
    Fuzzer.fuzzReader(is);
  }

  @Test
  public void testMessageLength() throws IOException {
    checkReader("x8fHx8fHCgo=");
  }

  @Test
  public void testNegativeLength() throws IOException {
    checkReader(
        "AP8A/QAAAAAAAAEAAAAAAAAAAAAAAAAAGBgMEmAYGBgYGAAYBhICGBgYPRgABQoABQoKluzs7Oz/////AQUAAAAqAA"
        + "AAAAAAAAAAABgYAhJgGBgYGBgAGAYwMDAwMDAwSDAwAAAYGAwSYBgYGBgYABgGEgIYGBg9GAAFCgAFCgEFAAAAAA"
        + "UKGAAAChgYGA==");
  }
}
