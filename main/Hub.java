package ru.eustas.nopbworker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import ru.eustas.nopbworker.WorkRequest.Input;

/**
 * Protobuf replacement for Bazel Worker protocol.
 * 
 * <p>Usage: pass {@link System#in}, {@link System#out} to {@link Hub} constructor and then use
 * {@link #readRequest} and {@link #writeResponse} in a loop.
 */
public class Hub {

  /**
   * This class contains all logic for parsing input and producing {@link WorkRequest} instances.
   */
  private static class Reader {
    /**
     * Single message (work request) serializes size limit.
     * 
     * <p>256MiB should be enough for any sane input.
     */
    private static final long MESSAGE_SIZE_CAP = 1 << 28;

    /**
     * Source.
     */
    final InputStream in;

    /**
     * Once input stream is "broken", it impossible to recover.
     */
    boolean broken = false;

    /**
     * Becomes {@code true} once {@link #in} is depleted.
     */
    boolean closed = false;

    /**
     * Current message size.
     */
    int size;

    /**
     * Parsing position in current message.
     */
    int pos;

    /**
     * Last parsed "varint" ({@link #readVarint} output).
     */
    long varint;

    /**
     * Message buffer.
     * 
     * <p>Parsing of is started only after the complete message is buffered (read from {@link #in}).
     */
    byte[] buffer = new byte[4096];

    /**
     * Constructor.
     */
    Reader(InputStream in) {
      this.in = in;
    }

    /**
     * Throws {@link IOException} and marks this instance as "broken".
     */
    void markBroken() throws IOException {
      broken = true;
      throw new IOException("Corrupted input stream");
    }

    /**
     * Read size of next message.
     * 
     * <p>Technically, this method duplicates the logic of {@link #readVarint}, but reads directly
     * from {@link #in} and does not require buffering.
     * 
     * @return {@code false} if input is over (there are no more messages in {@link #in} stream),
     *         otherwise {@code true} and fills {@link #size} with expected message size
     */
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
      if ((result < 0) || (result > MESSAGE_SIZE_CAP)) {
        markBroken();  // Message is too long / negative length.
      }
      size = (int) result;
      return true;
    }

    /**
     * Parses single "varint".
     * 
     * <p>{@link #varint} value is set in case of successful parsing.
     *
     * @param limit first byte after current (sub-)message position
     */
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

    /**
     * Checks if it is legal to read/skip variable-sized section.
     *
     * <p>Section length should be specified in {@link #varint}, likely set by last
     * {@link #readVarint} invocation.
     *
     * @param limit first byte after current (sub-)message position
     */
    void checkLength(int limit) throws IOException {
      if ((varint > MESSAGE_SIZE_CAP) || (pos + (int) varint > limit)) {
        markBroken();  // Incomplete message.
      }
    }

    /**
     * Skip section with specified wire-type.
     * 
     * <p>If tag id is not known its contents should be skipped. This method moves {@link #pos}
     * forward according to provided wire-type.
     * 
     * @param wireType tag wire-type
     * @param limit first byte after current (sub-)message position
     */
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
          markBroken();  // Unknown/unsupported (e.g. groups) wire-type.
      }
      if (pos > limit) {
        markBroken();  // Incomplete message.
      }
    }

    /**
     * "Input" submessage parsing loop.
     * 
     * @param obj output object (to fill with data)
     * @param limit first byte after current (sub-)message position
     */
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

    /**
     * "WorkRequest" submessage parsing loop.
     * 
     * @param obj output object (to fill with data)
     */
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

    /**
     * Read and parse next {@link WorkRequest}.
     * 
     * <p>{@see Hub#readRequest}
     */
    synchronized WorkRequest readRequest() throws IOException {
      // Noting to read.
      if (closed) {
        return null;
      }
      // Already failed.
      if (broken) {
        markBroken();
      }
      // Parse message size / detect EOF.
      if (!readSize()) {
        closed = true;
        return null;
      }
      // Resize buffer, if necessary.
      int newBufferSize = Integer.highestOneBit(size);
      if (newBufferSize < size) {
        newBufferSize *= 2;
      }
      if (newBufferSize > buffer.length) {
        buffer = new byte[newBufferSize];
      }
      // Read message, propagate exception, if any.
      int readBytes = 0;
      if (size > 0) {
        try {
          readBytes = in.read(buffer, 0, size);
        } catch (IOException ex) {
          broken = true;
          throw ex;
        }
      }
      // Premature EOF.
      if (readBytes != size) {
        markBroken();  // Incomplete message.
      }
      // Do actual parsing.
      WorkRequest result = new WorkRequest();
      pos = 0;
      parseRequest(result);
      return result;
    }
  }

  /**
   * This class contains all logic for serializing  and writing {@link WorkResponse} instances.
   */
  private static class Writer {
    /**
     * Sink. 
     */
    final OutputStream out;

    /**
     * Intermediate buffer.
     * 
     * <p>It consists of 2 parts: first 5 bytes are used for message size, remaining 30 for message
     * itself (excluding {@link WorkResponse#output} contents).
     * 
     * <p> 30
     *   = (1 + 10) for {@link WorkResponse#exitCode}
     *   + (1 + 5) for {@link WorkResponse#output}
     *   + (1 + 10) for {@link WorkResponse#requestId}
     *   + (1 + 1) for {@link WorkResponse#wasCancelled}
     */
    private final byte[] buffer = new byte[35];

    /**
     * Current position to write varint to.
     */
    int pos;

    /**
     * Constructor.
     */
    Writer(OutputStream out) {
      this.out = out;
    }

    /**
     * Encodes varint.
     * 
     * <p>Negative values produce 10 bytes (7 * 10 >= 64).
     * <p>Positive int values produce up to 5 bytes (7 * 5 >= 32).
     * <p>Boolean produce 1 byte.
     */
    void encodeInt(long value) {
      int numBits = 64 - Long.numberOfLeadingZeros(value);
      int numBytes = Math.max(1, (numBits + 6) / 7);
      for (int i = 0; i < numBytes; ++i) {
        int b = (i + 1 < numBytes) ? 0x80 : 0;
        b |= (value >>> (7 * i)) & 0x7F;
        buffer[pos++] = (byte) b;
      }
    }

    /**
     * Serialize next {@link WorkResponse}.
     * 
     * {@see Hub#writeResponse}
     */
    synchronized void writeResponse(WorkResponse obj) throws IOException {
      // Normalize output.
      String output = (obj.output == null) ? "" : obj.output;
      byte[] outputBytes = output.getBytes(StandardCharsets.UTF_8);

      // First 5 bytes are used for message size.
      pos = 5;

      // Serialize only non-default field value.
      if (obj.exitCode != 0) {
        encodeInt(8);  // exitCode: id = 1, wire-type = 0
        encodeInt(obj.exitCode);
      }

      // Serialize metadata for "output".
      encodeInt(18);  // output: id = 2, wire-type = 2
      encodeInt(outputBytes.length);
      // Remember position where to inject contents.
      int stop = pos;
  
      // Serialize only non-default field value.
      if (obj.requestId != 0) {
        encodeInt(24);  // requestId: id = 3, wire-type = 0
        encodeInt(obj.requestId);
      }

      // Serialize only non-default field value.
      if (obj.wasCancelled) {
        encodeInt(32);  // wasCancelled: id = 4, wire-type = 0
        encodeInt(obj.wasCancelled ? 1 : 0);
      }

      // Remember how much data is produced.
      int lastPos = pos;
      int encodedLength = lastPos - 5;
      // Serialize message size.
      pos = 0;
      encodeInt(encodedLength + outputBytes.length);
      // Move serialized message size to make it stick to main part.
      int newPos = 5;
      while (pos > 0) {
        buffer[--newPos] = buffer[--pos];
      }

      // Dump message size and the beginning of the message.
      out.write(buffer, newPos, stop - newPos);
      // Inject "output" contents.
      out.write(outputBytes);
      // Dump the remaining part of message.
      out.write(buffer, stop, lastPos - stop);
      // Flush to make sure Bazel receives responses ASAP.
      out.flush();
    }
  }

  /**
   * Reader instance.
   */
  private final Reader reader;

  /**
   * Writer instance.
   */
  private final Writer writer;

  /**
   * Constructor.
   */
  public Hub(InputStream in, OutputStream out) {
    this.reader = new Reader(in);
    this.writer = new Writer(out);
  }

  /**
   * Read and parse next {@link WorkRequest}.
   *
   * <p>Method is synchronized / blocking, so it is safe to request read from multiple threads.
   * 
   * @return {@code null} if no more messages are encoded in stream, otherwise next parsed work item
   * @throws IOExeption if reading stream causes it, parsing fails (corrupted stream), or either of
   *                    those happened in previous invocation
   */
  public WorkRequest readRequest() throws IOException {
    return reader.readRequest();
  }

  /**
   * Serialize and send (write) next {@link WorkResponse}.
   * 
   * <p>Method is synchronized, so it is safe to request write from multiple threads.
   *
   * @param obj item to serialize
   */
  public void writeResponse(WorkResponse response) throws IOException {
    writer.writeResponse(response);
  }
}