// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Asset access. With libandroidfw ({@link ResEngine}) names resolve inside
 * the retained archives exactly as on Android: {@link #open} reads
 * {@code assets/<name>}, {@link #openXmlResourceParser} reads any compiled
 * XML entry, and {@link #openFd} returns the archive itself with the stored
 * entry's offset and length.
 *
 * <p>The legacy engine serves the staged code dir ({@code -Disakai.codeDir})
 * with bounds: names are rejected when absolute, containing {@code ..}, or
 * escaping the code dir after canonicalization. Missing files throw
 * {@link IOException}.
 */
public class AssetManager implements AutoCloseable {
    public static final int ACCESS_UNKNOWN = 0;
    public static final int ACCESS_RANDOM = 1;
    public static final int ACCESS_STREAMING = 2;
    public static final int ACCESS_BUFFER = 3;

    private final ResEngine engine;
    private final File codeDir;

    public AssetManager() {
        String dir = System.getProperty("isakai.codeDir", "");
        this.engine = null;
        this.codeDir = (dir == null || dir.isEmpty()) ? null : new File(dir);
    }

    AssetManager(ResEngine engine) {
        this.engine = engine;
        this.codeDir = null;
    }

    /** Borrowed native AssetManager2 handle used by the NDK AAssetManager ABI. */
    long nativePtr() {
        return engine == null ? 0 : engine.ptr();
    }

    private File resolve(String fileName) throws IOException {
        if (fileName == null || fileName.isEmpty()) throw new IOException("bad asset name");
        if (codeDir == null) throw new IOException("unsupported: " + fileName);
        if (fileName.startsWith("/") || fileName.contains("..") || fileName.contains("\\")) {
            throw new IOException("bad asset name: " + fileName);
        }
        File f = new File(codeDir, fileName);
        String canon;
        String base;
        try {
            canon = f.getCanonicalPath();
            base = codeDir.getCanonicalPath();
        } catch (IOException e) {
            throw new IOException("bad asset name: " + fileName, e);
        }
        if (!canon.equals(base) && !canon.startsWith(base + File.separator)) {
            throw new IOException("bad asset name: " + fileName);
        }
        if (!f.isFile()) throw new IOException("missing asset: " + fileName);
        return f;
    }

    private ResEngine.NativeAsset openNative(String path, int accessMode) throws FileNotFoundException {
        ResEngine.NativeAsset asset = engine.openAsset(path,
                accessMode == ACCESS_UNKNOWN ? ResEngine.ACCESS_STREAMING : accessMode);
        if (asset == null) throw new FileNotFoundException("Asset file: " + path);
        return asset;
    }

    /** Open {@code assets/<fileName>} (legacy: a staged code-dir path) as a stream. */
    public InputStream open(String fileName) throws IOException {
        return open(fileName, ACCESS_STREAMING);
    }

    public InputStream open(String fileName, int accessMode) throws IOException {
        if (engine == null) return Files.newInputStream(resolve(fileName).toPath());
        if (fileName == null) throw new NullPointerException("fileName");
        return new AssetInputStream(openNative("assets/" + fileName, accessMode));
    }

    /** Decode a compiled (or, legacy, plain) XML entry. */
    public XmlResourceParser openXmlResourceParser(String fileName) throws IOException {
        return openXmlResourceParser(0, fileName);
    }

    public XmlResourceParser openXmlResourceParser(int cookie, String fileName) throws IOException {
        if (engine != null) {
            ResEngine.XmlBlock block = engine.openXml(cookie, fileName);
            if (block == null) throw new FileNotFoundException("Invalid XML resource: " + fileName);
            return new ResXmlParser(block, fileName);
        }
        try (InputStream input = open(fileName)) {
            byte[] bytes = input.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("XML exceeds 4 MiB");
            try {
                return Resources.decodeBinaryXml(bytes, fileName);
            } catch (Resources.NotFoundException failure) {
                throw new IOException("Invalid XML resource: " + fileName, failure);
            }
        }
    }

    /**
     * Descriptor for an asset. With libandroidfw this is the archive opened
     * read-only with the stored entry's start offset and length, as on
     * Android; a compressed entry cannot be opened this way. The legacy
     * staged tree is already extracted, so the descriptor views the whole
     * file.
     */
    public AssetFileDescriptor openFd(String fileName) throws IOException {
        if (engine == null) return descriptor(resolve(fileName), 0, -1);
        if (fileName == null) throw new NullPointerException("fileName");
        try (ResEngine.NativeAsset asset = openNative("assets/" + fileName, ACCESS_RANDOM)) {
            long[] range = asset.range();
            if (range == null) {
                throw new FileNotFoundException(
                        "This file can not be opened as a file descriptor; it is probably compressed");
            }
            return descriptor(asset.archive, range[0], range[1]);
        }
    }

    private static AssetFileDescriptor descriptor(File file, long start, long length) throws IOException {
        android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            long len = length < 0 ? pfd.getStatSize() : length;
            return new AssetFileDescriptor(pfd, start, len);
        } catch (RuntimeException | Error failure) {
            try {
                pfd.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /** Entries directly under {@code assets/<path>} (legacy: the staged code dir). */
    public String[] list(String path) {
        if (engine != null) return engine.list(path == null ? "" : path);
        if (codeDir == null) return new String[0];
        String rel = (path == null || path.isEmpty()) ? "" : path;
        if (rel.startsWith("/") || rel.contains("..") || rel.contains("\\")) return new String[0];
        File dir = rel.isEmpty() ? codeDir : new File(codeDir, rel);
        try {
            String canon = dir.getCanonicalPath();
            String base = codeDir.getCanonicalPath();
            if (!canon.equals(base) && !canon.startsWith(base + File.separator)) return new String[0];
        } catch (IOException e) {
            return new String[0];
        }
        String[] names = dir.list();
        if (names == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (!n.equals("classes") && !n.equals("AndroidManifest.xml")
                    && !n.equals("resources.arsc") && !n.equals("base.apk") && !n.startsWith(".")) {
                out.add(n);
            }
        }
        return out.toArray(new String[0]);
    }

    /** A fresh table over the same archives, configured independently. */
    ResourceTable newTable() {
        if (engine == null) return Resources.Table.loadStaged();
        ResEngine fork = engine.reopen();
        return fork == null ? null : new AndroidfwTable(fork);
    }

    /** The engine is shared with Resources and lives as long as it does. */
    @Override
    public void close() {}

    /** Stream over a native asset; closing it releases the asset handle. */
    public static final class AssetInputStream extends InputStream {
        private final ResEngine.NativeAsset asset;
        private long mark;
        private boolean marked;

        AssetInputStream(ResEngine.NativeAsset asset) {
            this.asset = asset;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            java.util.Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) return 0;
            int n = asset.read(b, off, len);
            if (n < 0) throw new IOException("asset read failed");
            return n == 0 ? -1 : n;
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) return 0;
            long before = asset.remaining();
            long skip = Math.min(n, before);
            if (asset.seek(skip, 1) < 0) throw new IOException("asset seek failed");
            return skip;
        }

        @Override
        public int available() {
            long remaining = asset.remaining();
            return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized void mark(int readlimit) {
            mark = asset.length() - asset.remaining();
            marked = true;
        }

        @Override
        public synchronized void reset() throws IOException {
            if (!marked) throw new IOException("reset without mark");
            if (asset.seek(mark, 0) < 0) throw new IOException("asset seek failed");
        }

        @Override
        public void close() {
            asset.close();
        }
    }
}
