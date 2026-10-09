// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Minimal Application: lifecycle + private file storage. M1 headless subset. */
public class Application {
    private String dataDir;
    /** Called by IsakaiMain before any component runs. */
    public void attach(String dataDir) {
        this.dataDir = dataDir;
    }

    public void onCreate() {}

    public String getDataDir() {
        return dataDir;
    }

    public Path file(String name) {
        return Paths.get(dataDir, name);
    }

    public String readFile(String name, String fallback) {
        try {
            Path p = file(name);
            if (!Files.exists(p)) return fallback;
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fallback;
        }
    }

    public void writeFile(String name, String content) {
        try {
            Path p = file(name);
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(name + ".tmp");
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, p, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("write " + name + ": " + e.getMessage(), e);
        }
    }
}
