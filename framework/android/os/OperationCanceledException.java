// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Thrown when an operation is canceled. */
public class OperationCanceledException extends RuntimeException {
    public OperationCanceledException() { super("operation canceled"); }
    public OperationCanceledException(String message) { super(message); }
    public OperationCanceledException(String message, Throwable cause) { super(message, cause); }
}
