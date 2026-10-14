// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

/** Random-access input contract; a consumer must explicitly support this source. */
public abstract class MediaDataSource implements java.io.Closeable {
    public abstract int readAt(long position, byte[] buffer, int offset, int size) throws java.io.IOException;
    public abstract long getSize() throws java.io.IOException;
    public abstract void close() throws java.io.IOException;
}
