package ru.eustas.nopbworker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConformanceCheckerRunner {
  public static void main(String[] args) throws IOException {
    byte[] data = Files.readAllBytes(Paths.get(args[0]));
    ConformanceChecker.fuzzerTestOneInput(data);
  }
}
