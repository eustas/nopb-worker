package ru.eustas.nopbworker;

import java.util.ArrayList;
import java.util.List;

/**
 * This represents a single work unit that Blaze sends to the worker.
 */
public class WorkRequest {
  /**
   * An input file.
   */
  public static class Input {
    /**
     * The path in the file system where to read this input artifact from.
     *
     * This is either a path relative to the execution root (the worker process is launched with the
     * working directory set to the execution root), or an absolute path.
     */
    public String path = "";

    /**
     * A hash-value of the contents.
     *
     * The format of the contents is unspecified and the digest should be treated as an opaque
     * token.
     */
    public byte[] digest = new byte[0];
  }

  public List<String> arguments = new ArrayList<>();

  /**
   * The inputs that the worker is allowed to read during execution of this request.
   */
  public List<Input> inputs = new ArrayList<>();

  /**
   * Each WorkRequest must have either a unique request_id or request_id = 0.
   *
   * If requestId is 0, this WorkRequest must be processed alone (singleplex), otherwise the worker
   * may process multiple WorkRequests in parallel (multiplexing). As an exception to the above, if
   * the cancel field is true, the requestId must be the same as a previously sent WorkRequest. The
   * requestId must be attached unchanged to the corresponding WorkResponse. Only one singleplex
   * request may be sent to a worker at a time.
   */
  public int requestId;

  /**
   * EXPERIMENTAL: When true, this is a cancel request, indicating that a previously sent WorkRequest with the same request_id should be cancelled.
   *
   * The arguments and inputs fields must be empty and should be ignored.
   */
  public boolean cancel;
}
