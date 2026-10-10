// SPDX-License-Identifier: GPL-3.0-or-later
package android.security.identity;

/** Presentation session shell. */
public abstract class PresentationSession implements AutoCloseable {
    public abstract byte[] getDeviceEngagement();
    @Override public void close() { }
}
