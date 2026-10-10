// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.os.ParcelFileDescriptor;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/** Asset file descriptor: offset/length view over a ParcelFileDescriptor. */
public class AssetFileDescriptor implements Closeable {
    public static final long UNKNOWN_LENGTH = -1;

    private final ParcelFileDescriptor descriptor;
    private final long startOffset;
    private final long length;

    public AssetFileDescriptor(ParcelFileDescriptor descriptor, long startOffset, long length) {
        this.descriptor = descriptor;
        this.startOffset = startOffset;
        this.length = length;
    }

    public ParcelFileDescriptor getParcelFileDescriptor() {
        return descriptor;
    }

    public FileDescriptor getFileDescriptor() {
        return descriptor == null ? new FileDescriptor() : descriptor.getFileDescriptor();
    }

    public long getStartOffset() {
        return startOffset;
    }

    public long getLength() {
        return length;
    }

    public long getDeclaredLength() {
        return length;
    }

    public FileInputStream createInputStream() throws IOException {
        if (descriptor == null) throw new IOException("closed");
        descriptor.checkError();
        return new FileInputStream(descriptor.getFileDescriptor());
    }

    @Override
    public void close() throws IOException {
        if (descriptor != null) descriptor.close();
    }
}
