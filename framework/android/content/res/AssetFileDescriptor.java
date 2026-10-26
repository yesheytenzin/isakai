// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

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
        return new AutoCloseInputStream(this);
    }

    /** Bounded asset view; the raw descriptor and channel still address the archive. */
    public static class AutoCloseInputStream extends FileInputStream {
        private final AssetFileDescriptor asset;
        private long remaining;

        public AutoCloseInputStream(AssetFileDescriptor asset) throws IOException {
            super(asset.getFileDescriptor());
            this.asset = asset;
            try {
                if (asset.descriptor == null) throw new IOException("closed");
                asset.descriptor.checkError();
                getChannel().position(asset.startOffset);
                remaining = asset.length;
            } catch (IOException | RuntimeException | Error failure) {
                try {
                    close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }

        @Override
        public int read() throws IOException {
            asset.descriptor.checkError();
            if (remaining == 0) return -1;
            int value = super.read();
            if (value < 0) remaining = 0;
            else if (remaining > 0) remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            Objects.checkFromIndexSize(offset, count, buffer.length);
            if (count == 0) return 0;
            asset.descriptor.checkError();
            if (remaining == 0) return -1;
            int requested = remaining < 0 ? count : (int) Math.min(count, remaining);
            int read = super.read(buffer, offset, requested);
            if (read < 0) remaining = 0;
            else if (remaining > 0) remaining -= read;
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            if (count <= 0) return 0;
            asset.descriptor.checkError();
            long skipped = super.skip(remaining < 0 ? count : Math.min(count, remaining));
            if (remaining > 0) remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            asset.descriptor.checkError();
            int available = super.available();
            return remaining < 0 ? available : (int) Math.min(available, remaining);
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transferTo(output);
            return output.toByteArray();
        }

        @Override
        public byte[] readNBytes(int count) throws IOException {
            if (count < 0) throw new IllegalArgumentException("negative byte count");
            byte[] buffer = new byte[Math.min(count, 8192)];
            ByteArrayOutputStream output = new ByteArrayOutputStream(buffer.length);
            while (count > 0) {
                int read = read(buffer, 0, Math.min(count, buffer.length));
                if (read < 0) break;
                output.write(buffer, 0, read);
                count -= read;
            }
            return output.toByteArray();
        }

        @Override
        public long transferTo(OutputStream output) throws IOException {
            Objects.requireNonNull(output);
            byte[] buffer = new byte[8192];
            long transferred = 0;
            int read;
            while ((read = read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                transferred += read;
            }
            return transferred;
        }

        @Override
        public void close() throws IOException {
            try {
                asset.close();
            } finally {
                super.close();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (descriptor != null) descriptor.close();
    }
}
