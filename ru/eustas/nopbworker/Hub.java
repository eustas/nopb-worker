package ru.eustas.nopbworker;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import ru.eustas.nopbworker.WorkRequest.Input;

public class Hub {

  private static class Reader {
    // 1GiB should be enough for everything.
    private static final long MESSAGE_SIZE_CAP = 1 << 30;

    final InputStream in;
    boolean broken = false;
    boolean closed = false;
    int size;
    int pos;
    long varint;
    byte[] buffer = new byte[4096];

    Reader(InputStream in) {
      this.in = in;
    }

    void markBroken() throws IOException {
      broken = true;
      throw new IOException("Corrupted input stream");
    }

    boolean readSize() throws IOException {
      long result = 0;
      int length = 0;
      while (true) {
        int next = in.read();
        if (next == -1) {
          if (length == 0) {
            return false;
          } else {
            markBroken();  // Incomplete message size tag.
          }
        }
        if (length >= 9) { // 7 * 9 == 63 bits already.
          markBroken();  // Size tag is too long.
        }
        long payload = next & 0x7F;
        result = result | (payload << (length * 7));
        length++;
        if ((next & 0x80) == 0) {
          break;
        }
      }
      if (length > MESSAGE_SIZE_CAP) {
        markBroken();  // Message is too long.
      }
      size = (int) result;
      return true;
    }

    void readVarint(int limit) throws IOException {
      long result = 0;
      int length = 0;
      while (true) {
        if (length == 10) { // 10 * 7 > 64
          markBroken();  // Too long varint.
        }
        if (pos >= limit) {
          markBroken();  // Incomplete varint.
        }
        int next = buffer[pos++] & 0xFF;
        long payload = next & 0x7F;
        result = result | (payload << (length * 7));
        if ((length == 9) && ((payload & ~1) != 0)) {
          markBroken();  // Invalid varint.
        }
        length++;
        if ((next & 0x80) == 0) {
          break;
        }
      }
      varint = result;
    }

    void checkLength(int limit) throws IOException {
      if ((varint > MESSAGE_SIZE_CAP) || (pos + (int) varint > limit)) {
        markBroken();  // Incomplete message.
      }
    }

    void dropTag(int wireType, int limit) throws IOException {
      switch (wireType) {
        case 0: // int32, int64, uint32, uint64, sint32, sint64, bool, enum
          readVarint(limit);
          break;

        case 1: // fixed64, sfixed64, double
          pos += 8;
          break;

        case 2: // string, bytes, embedded messages, packed repeated fields
          readVarint(limit);
          checkLength(limit);
          pos += (int) varint;
          break;

        case 5: // fixed32, sfixed32, float
          pos += 4;
          break;

        default:
          markBroken();  // Unknown/unsupported wire-type.
      }
      if (pos > limit) {
        markBroken();  // Incomplete message.
      }
    }

    void parseInput(Input obj, int limit) throws IOException {
      while (pos < limit) {
        readVarint(limit);
        int wireType = (int) varint & 0x7;
        long id = varint >> 3;
        if (id == 1) { // oath
          if (wireType != 2) {
            markBroken();  // Inconsistent message.
          }
          readVarint(limit);
          checkLength(limit);
          obj.path = new String(buffer, pos, (int) varint, StandardCharsets.UTF_8);
          pos += (int) varint;
        } else if (id == 2) { // digest
          if (wireType != 2) {
            markBroken();  // Inconsistent message.
          }
          readVarint(limit);
          checkLength(limit);
          obj.digest = new byte[(int) varint];
          System.arraycopy(buffer, pos, obj.digest, 0, (int) varint);
          pos += (int) varint;
        } else {
          dropTag(wireType, limit);
        }
      }
    }

    void parseRequest(WorkRequest obj) throws IOException {
      while (pos < size) {
        readVarint(size);
        int wireType = (int) varint & 0x7;
        long id = varint >> 3;
        if (id == 1) { // arguments
          if (wireType != 2) {
            markBroken();  // Inconsistent message.
          }
          readVarint(size);
          checkLength(size);
          obj.arguments.add(new String(buffer, pos, (int) varint, StandardCharsets.UTF_8));
          pos += (int) varint;
        } else if (id == 2) { // inputs
          if (wireType != 2) {
            markBroken();  // Inconsistent message.
          }
          readVarint(size);
          checkLength(size);
          Input input = new Input();
          obj.inputs.add(input);
          parseInput(input, pos + (int) varint);
        } else if (id == 3) { // requestId
          if (wireType != 0) {
            markBroken();  // Inconsistent message.
          }
          readVarint(size);
          // TODO(eustas): sanitize?
          obj.requestId = (int) varint;
        } else if (id == 4) { // cancel
          if (wireType != 0) {
            markBroken();  // Inconsistent message.
          }
          readVarint(size);
          obj.cancel = (varint != 0);
        } else {
          dropTag(wireType, size);
        }
      }
    }

    synchronized WorkRequest readRequest() throws IOException {
      if (closed) {
        return null;
      }
      if (broken) {
        markBroken();
      }
      if (!readSize()) {
        closed = true;
        return null;
      }
      int newBufferSize = Integer.highestOneBit(size);
      if (newBufferSize < size) {
        newBufferSize *= 2;
      }
      if (newBufferSize > buffer.length) {
        buffer = new byte[newBufferSize];
      }
      int readBytes = 0;
      if (size > 0) {
        try {
          readBytes = in.read(buffer, 0, size);
        } catch (IOException ex) {
          broken = true;
          throw ex;
        }
      }
      if (readBytes != size) {
        markBroken();  // Incomplete message.
      }
      WorkRequest result = new WorkRequest();
      pos = 0;
      parseRequest(result);
      return result;
    }
  }

  private static class Writer {
    final PrintStream out;
    private final byte[] buffer = new byte[25];
    int pos;
    Writer(PrintStream out) {
      this.out = out;
    }

    void encodeInt(int value) {
      int numBits = 32 - Integer.numberOfLeadingZeros(value);
      int numBytes = Math.max(1, (numBits + 6) / 7);
      for (int i = 0; i < numBytes; ++i) {
        int b = (i + 1 < numBytes) ? 0 : 0x80;
        b |= value & 0x7F;
        value >>= 7;
        buffer[pos++] = (byte) b;
      }
    }

    synchronized void write(WorkResponse obj) throws IOException {
      String output = (obj.output == null) ? "" : obj.output;
      byte[] outputBytes = output.getBytes(StandardCharsets.UTF_8);

      pos = 5;
      encodeInt(8);  // exitCode: id = 1, wire-type = 0
      encodeInt(obj.exitCode);
      // output: potsponed
      encodeInt(24);  // requestId: id = 3, wire-type = 0
      encodeInt(obj.requestId);
      encodeInt(32);  // wasCancelled: id = 4, wire-type = 0
      encodeInt(obj.wasCancelled ? 1 : 0);
      encodeInt(32);  // wasCancelled: id = 4, wire-type = 0
      encodeInt(18);  // output: id = 2, wire-type = 2
      encodeInt(outputBytes.length);
      // Output itself is dumped separately.

      int lastPos = pos;
      int encodedLength = lastPos - 5;
      pos = 0;
      encodeInt(encodedLength + outputBytes.length);
      int newPos = 5;
      while (pos > 0) {
        buffer[--newPos] = buffer[--pos];
      }

      out.write(buffer, newPos, lastPos - newPos);
      out.write(outputBytes);
      out.flush();
    }
  }

  private final Reader reader;
  private final Writer writer;

  public Hub(InputStream in, PrintStream out) {
    this.reader = new Reader(in);
    this.writer = new Writer(out);
  }

  public WorkRequest readRequest() throws IOException {
    return reader.readRequest();
  }

  public void writeResponse(WorkResponse response) throws IOException {
    writer.write(response);
  }
}