// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * File-descriptor handle over a real open file. Fail-closed: open
 * rejects null paths, directories, and unknown modes; reads/writes
 * after close throw.
 */
public class ParcelFileDescriptor implements Closeable {
    public static final int MODE_READ_ONLY = 0x10000000;
    public static final int MODE_WRITE_ONLY = 0x20000000;
    public static final int MODE_READ_WRITE = 0x30000000;
    public static final int MODE_CREATE = 0x08000000;
    public static final int MODE_TRUNCATE = 0x04000000;
    public static final int MODE_APPEND = 0x02000000;

    private final FileDescriptor descriptor;
    private final RandomAccessFile file;
    private final int mode;
    private final int fd;
    private boolean closed;

    private static int nextFd = 100;

    public ParcelFileDescriptor(FileDescriptor descriptor, int fd) {
        this.descriptor = descriptor == null ? new FileDescriptor() : descriptor;
        this.file = null;
        this.mode = MODE_READ_ONLY;
        this.fd = fd;
    }

    private ParcelFileDescriptor(RandomAccessFile file, int mode, int fd) throws IOException {
        this.file = file;
        this.descriptor = file.getFD();
        this.mode = mode;
        this.fd = fd;
    }

    public ParcelFileDescriptor(ParcelFileDescriptor src) {
        this.file = src == null ? null : src.file;
        this.descriptor = src == null || src.descriptor == null ? new FileDescriptor() : src.descriptor;
        this.mode = src == null ? MODE_READ_ONLY : src.mode;
        this.fd = src == null ? -1 : src.fd;
    }

    public FileDescriptor getFileDescriptor() {
        return descriptor;
    }

    public int getFd() {
        return fd;
    }

    public long getStatSize() {
        if (file == null) return -1;
        try {
            return file.length();
        } catch (IOException e) {
            return -1;
        }
    }

    public int getMode() {
        return mode;
    }

    public void checkError() throws IOException {
        if (closed) throw new IOException("closed");
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (file != null) file.close();
    }

    public boolean isClosed() {
        return closed;
    }

    /** Translate an AOSP mode string ("r", "w", "rw", "rwt", "wa", ...) to flags. */
    public static int parseMode(String mode) throws IOException {
        if (mode == null) throw new IOException("null mode");
        switch (mode) {
            case "r": return MODE_READ_ONLY;
            case "w": return MODE_WRITE_ONLY | MODE_CREATE | MODE_TRUNCATE;
            case "wt": return MODE_WRITE_ONLY | MODE_CREATE | MODE_TRUNCATE;
            case "wa": return MODE_WRITE_ONLY | MODE_CREATE | MODE_APPEND;
            case "rw": return MODE_READ_WRITE | MODE_CREATE;
            case "rwt": return MODE_READ_WRITE | MODE_CREATE | MODE_TRUNCATE;
            case "rwa": return MODE_READ_WRITE | MODE_CREATE | MODE_APPEND;
            default: throw new IOException("bad mode " + mode);
        }
    }

    public static ParcelFileDescriptor open(File file, int mode) throws IOException {
        if (file == null) throw new IOException("null file");
        boolean read = (mode & MODE_READ_WRITE) == MODE_READ_ONLY
                || (mode & MODE_READ_WRITE) == MODE_READ_WRITE;
        boolean write = (mode & MODE_READ_WRITE) != MODE_READ_ONLY;
        if (!read && !write) throw new IOException("bad mode " + Integer.toHexString(mode));
        if (file.isDirectory()) throw new IOException("is a directory: " + file);
        if ((mode & MODE_CREATE) != 0) {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            if (!file.exists() && !file.createNewFile()) {
                throw new IOException("cannot create " + file);
            }
        } else if (!file.exists()) {
            throw new IOException("no such file " + file);
        }
        String rafMode = write ? (read ? "rw" : "rw") : "r";
        RandomAccessFile raf = new RandomAccessFile(file, rafMode);
        try {
            if ((mode & MODE_TRUNCATE) != 0 && write) raf.setLength(0);
            if ((mode & MODE_APPEND) != 0) raf.seek(raf.length());
            int fd;
            synchronized (ParcelFileDescriptor.class) {
                fd = nextFd++;
            }
            return new ParcelFileDescriptor(raf, mode, fd);
        } catch (IOException | RuntimeException e) {
            try {
                raf.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    public interface OnCloseListener {
        void onClose(IOException e);
    }

    /** Input stream over this descriptor (shares the file offset). */
    public FileInputStream getInputStream() throws IOException {
        checkError();
        if (file == null) throw new IOException("no backing file");
        return new FileInputStream(descriptor);
    }

    /** Output stream over this descriptor (shares the file offset). */
    public FileOutputStream getOutputStream() throws IOException {
        checkError();
        if (file == null) throw new IOException("no backing file");
        return new FileOutputStream(descriptor);
    }

    public static ParcelFileDescriptor adoptFd(int fd) {
        return new ParcelFileDescriptor(new FileDescriptor(), fd);
    }

    public static ParcelFileDescriptor dup(FileDescriptor fd) throws IOException {
        if (fd == null) throw new IOException("null fd");
        return new ParcelFileDescriptor(fd, -1);
    }

    public ParcelFileDescriptor dup() throws IOException {
        checkError();
        return new ParcelFileDescriptor(this);
    }
}
