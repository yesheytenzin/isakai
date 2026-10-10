// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.util.TypedValue;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Asset access rooted at the staged code dir ({@code -Disakai.codeDir}).
 *
 * <p>Serves staged {@code assets/} + {@code res/} paths with bounds: names
 * are rejected when absolute, containing {@code ..}, or escaping the code
 * dir after canonicalization. Missing files throw {@link IOException}.
 */
public class AssetManager implements AutoCloseable {
    private final File codeDir;

    public AssetManager() {
        String dir = System.getProperty("isakai.codeDir", "");
        this.codeDir = (dir == null || dir.isEmpty()) ? null : new File(dir);
    }

    /** Test hook: root at an explicit directory (null = unrooted). */
    AssetManager(File codeDir) {
        this.codeDir = codeDir;
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

    /** Open a staged asset (or res passthrough) as a stream. */
    public InputStream open(String fileName) throws IOException {
        return Files.newInputStream(resolve(fileName).toPath());
    }

    public InputStream open(String fileName, int accessMode) throws IOException {
        return open(fileName);
    }

    /**
     * Open a staged file with offset/length 0/size. Real APKs store assets
     * uncompressed with true offsets; the staged tree is already extracted,
     * so the descriptor views the whole file (fail-closed length, never -1).
     */
    public AssetFileDescriptor openFd(String fileName) throws IOException {
        File f = resolve(fileName);
        long len = f.length();
        android.os.ParcelFileDescriptor pfd;
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            pfd = new android.os.ParcelFileDescriptor(raf.getFD(), -1);
        } catch (IOException e) {
            throw new IOException("missing asset: " + fileName, e);
        }
        return new AssetFileDescriptor(pfd, 0, len);
    }

    /** List staged entries directly under {@code path} (assets/ or res/). */
    public String[] list(String path) {
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
                    && !n.equals("resources.arsc") && !n.startsWith(".")) {
                out.add(n);
            }
        }
        return out.toArray(new String[0]);
    }

    public java.io.FileDescriptor openFdDescriptor(String fileName) {
        return new java.io.FileDescriptor();
    }

    @Override
    public void close() {}
}
