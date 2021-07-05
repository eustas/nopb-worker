package ru.eustas.nopbworker;

import java.util.Objects;

/**
 * The worker sends this message to Blaze when it finished its work on the WorkRequest message.
 *
 * This class is a replacement for com.google.devtools.build.lib.worker.WorkResponse protobuf.
 */
public class WorkResponse {
  public int exitCode;

  /**
   * This is printed to the user after the WorkResponse has been received and is supposed to contain
   * compiler warnings / errors etc. - thus we'll use a string type here, which gives us UTF-8
   * encoding.
   */
  public String output = "";

  /**
   * This field must be set to the same requestId as the WorkRequest it is a response to.
   *
   * Since worker processes which support multiplex worker will handle multiple WorkRequests in
   * parallel, this ID will be used to determined which WorkerProxy does this WorkResponse belong
   * to.
   */
  public int requestId;

  /**
   * EXPERIMENTAL When true, indicates that this response was sent due to receiving a cancel
   * request.
   *
   * The exit_code and output fields should be empty and will be ignored. Exactly one WorkResponse
   * must be sent for each non-cancelling WorkRequest received by the worker, but if the worker
   * received a cancel request, it doesn't matter if it replies with a regular WorkResponse or with
   * one where was_cancelled = true.
   */
  public boolean wasCancelled;

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof WorkResponse)) {
      return false;
    }
    WorkResponse other = (WorkResponse) o;
    return exitCode == other.exitCode && Objects.equals(output, other.output)
        && requestId == other.requestId && wasCancelled == other.wasCancelled;
  }

  @Override
  public int hashCode() {
    return Objects.hash(exitCode, output, requestId, wasCancelled);
  }
}
