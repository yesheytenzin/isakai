// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.isakai.prefs.IsakaiPrefsWork;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * File-backed SharedPreferences with the AOSP durable-write protocol.
 *
 * <p>Persistence: {@code <map>} root of {@code <string>}, {@code <int
 * value="">}, {@code <long>}, {@code <float>}, {@code <boolean>}, and
 * {@code <set>} (nested {@code <string>} items) children. The writer is
 * byte-exact with AOSP's {@code writeMapXml}: UTF-8, LF, two-space
 * attribute indentation. The strict reader treats any unknown tag as a
 * corrupt file: it logs, resets to empty, and rewrites on the next edit.
 *
 * <p>Durability: {@code .bak} rename-write-fsync-chmod(0600)-stat-unlink.
 * {@link #commit} latches on completion with a memory-first fast path
 * (returns true without disk I/O when nothing changed);
 * {@link #apply} queues to the single-thread
 * {@link IsakaiPrefsWork} executor. Queued work drains via
 * {@link IsakaiPrefsWork#waitToFinish()} (Activity lifecycle hook).
 */
public final class SharedPreferencesImpl implements SharedPreferences {
    private final File file;
    private final File backupFile;
    private final Object lock = new Object();
    private Map<String, Object> map;
    private final CopyOnWriteArrayList<OnSharedPreferenceChangeListener> listeners =
            new CopyOnWriteArrayList<>();

    public SharedPreferencesImpl(File file) {
        this.file = file;
        this.backupFile = new File(file.getPath() + ".bak");
        this.map = loadLocked();
    }

    @Override
    public Map<String, ?> getAll() {
        synchronized (lock) {
            return new HashMap<>(map);
        }
    }

    @Override
    public String getString(String key, String defValue) {
        synchronized (lock) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : defValue;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        synchronized (lock) {
            Object v = map.get(key);
            if (v instanceof Set) {
                return Collections.unmodifiableSet(new HashSet<>((Set<String>) v));
            }
            return defValues;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        synchronized (lock) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        synchronized (lock) {
            Object v = map.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        synchronized (lock) {
            Object v = map.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        synchronized (lock) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }
    }

    @Override
    public boolean contains(String key) {
        synchronized (lock) {
            return map.containsKey(key);
        }
    }

    @Override
    public Editor edit() {
        return new EditorImpl();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.addIfAbsent(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String key) {
        for (OnSharedPreferenceChangeListener l : listeners) {
            try {
                l.onSharedPreferenceChanged(this, key);
            } catch (RuntimeException e) {
                System.err.println("isakai: prefs listener failed for " + key + ": " + e);
            }
        }
    }

    // --- Load ---

    private Map<String, Object> loadLocked() {
        if (backupFile.exists()) {
            if (!file.exists()) {
                backupFile.renameTo(file);
            } else {
                backupFile.delete();
            }
        }
        if (!file.exists()) return new HashMap<>();
        try {
            return readFile(file);
        } catch (Exception e) {
            System.err.println("isakai: prefs " + file + " corrupt, resetting: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private static Map<String, Object> readFile(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc;
        try (FileInputStream in = new FileInputStream(file)) {
            doc = builder.parse(in);
        }
        Element root = doc.getDocumentElement();
        if (!"map".equals(root.getTagName())) {
            throw new IOException("unknown root tag: " + root.getTagName());
        }
        Map<String, Object> out = new HashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) node;
            String tag = el.getTagName();
            String name = el.getAttribute("name");
            switch (tag) {
                case "string":
                    out.put(name, el.getTextContent());
                    break;
                case "boolean":
                    out.put(name, Boolean.parseBoolean(el.getAttribute("value")));
                    break;
                case "int":
                    out.put(name, Integer.parseInt(el.getAttribute("value")));
                    break;
                case "long":
                    out.put(name, Long.parseLong(el.getAttribute("value")));
                    break;
                case "float":
                    out.put(name, Float.parseFloat(el.getAttribute("value")));
                    break;
                case "set": {
                    Set<String> set = new HashSet<>();
                    NodeList items = el.getChildNodes();
                    for (int j = 0; j < items.getLength(); j++) {
                        Node item = items.item(j);
                        if (item.getNodeType() != Node.ELEMENT_NODE) continue;
                        Element itemEl = (Element) item;
                        if (!"string".equals(itemEl.getTagName())) {
                            throw new IOException("unknown set item tag: " + itemEl.getTagName());
                        }
                        set.add(itemEl.getTextContent());
                    }
                    out.put(name, set);
                    break;
                }
                default:
                    throw new IOException("unknown tag: " + tag);
            }
        }
        return out;
    }

    // --- Writer (AOSP writeMapXml byte-exact: UTF-8, LF, two-space attribute indent) ---

    static void writeMapXml(Map<String, Object> map, OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n");
        sb.append("<map>\n");
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Object v = map.get(key);
            String ek = escape(key);
            if (v instanceof String) {
                sb.append("    <string name=\"").append(ek).append("\">")
                        .append(escapeText((String) v)).append("</string>\n");
            } else if (v instanceof Boolean) {
                sb.append("    <boolean name=\"").append(ek).append("\" value=\"")
                        .append(v).append("\" />\n");
            } else if (v instanceof Integer) {
                sb.append("    <int name=\"").append(ek).append("\" value=\"")
                        .append(v).append("\" />\n");
            } else if (v instanceof Long) {
                sb.append("    <long name=\"").append(ek).append("\" value=\"")
                        .append(v).append("\" />\n");
            } else if (v instanceof Float) {
                sb.append("    <float name=\"").append(ek).append("\" value=\"")
                        .append(v).append("\" />\n");
            } else if (v instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> set = (Set<String>) v;
                List<String> items = new ArrayList<>(set);
                Collections.sort(items);
                sb.append("    <set name=\"").append(ek).append("\">\n");
                for (String item : items) {
                    sb.append("        <string>").append(escapeText(item)).append("</string>\n");
                }
                sb.append("    </set>\n");
            } else {
                throw new IOException("unsupported prefs value: " + v);
            }
        }
        sb.append("</map>\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // --- Durable write protocol: .bak rename-write-fsync-chmod(0600)-stat-unlink ---

    private void writeToDisk(Map<String, Object> snapshot) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        try {
            if (file.exists()) {
                if (backupFile.exists()) backupFile.delete();
                if (!file.renameTo(backupFile)) {
                    System.err.println("isakai: prefs cannot backup " + file);
                    return;
                }
            }
            boolean ok = false;
            try (FileOutputStream out = new FileOutputStream(file)) {
                writeMapXml(snapshot, out);
                out.getFD().sync();
            }
            chmod0600(file);
            if (file.length() <= 0) {
                throw new IOException("empty write");
            }
            ok = true;
            if (ok) backupFile.delete();
        } catch (Exception e) {
            System.err.println("isakai: prefs write failed for " + file + ": " + e.getMessage());
            if (file.exists()) file.delete();
            if (backupFile.exists()) backupFile.renameTo(file);
        }
    }

    private static void chmod0600(File f) {
        f.setReadable(false, false);
        f.setWritable(false, false);
        f.setExecutable(false, false);
        f.setReadable(true, true);
        f.setWritable(true, true);
    }

    /** Visible for the host-side schema assertion: does a .bak exist right now? */
    boolean hasBackupForTest() {
        return backupFile.exists();
    }

    // --- Editor ---

    private final class EditorImpl implements Editor {
        private final Map<String, Object> staged = new HashMap<>();
        private final Set<String> removed = new HashSet<>();
        private boolean clearAll = false;

        @Override
        public Editor putString(String key, String value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            staged.put(key, values != null ? new HashSet<>(values) : null);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor remove(String key) {
            staged.remove(key);
            removed.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clearAll = true;
            staged.clear();
            removed.clear();
            return this;
        }

        @Override
        public boolean commit() {
            Map<String, Object> snapshot;
            Set<String> changed;
            synchronized (lock) {
                if (clearAll && staged.isEmpty()) {
                    if (map.isEmpty()) return true; // memory-first fast path
                    map = new HashMap<>();
                    changed = Collections.emptySet();
                } else {
                    Map<String, Object> next = clearAll ? new HashMap<>() : new HashMap<>(map);
                    for (String k : removed) next.remove(k);
                    next.putAll(staged);
                    if (next.equals(map)) return true; // memory-first fast path
                    changed = changedKeys(map, next);
                    map = next;
                }
                snapshot = new HashMap<>(map);
            }
            final CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object> toWrite = snapshot;
            IsakaiPrefsWork.enqueue(() -> {
                try {
                    writeToDisk(toWrite);
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            for (String k : changed) notifyListeners(k);
            return true;
        }

        @Override
        public void apply() {
            Map<String, Object> snapshot;
            Set<String> changed;
            synchronized (lock) {
                Map<String, Object> next = clearAll ? new HashMap<>() : new HashMap<>(map);
                for (String k : removed) next.remove(k);
                next.putAll(staged);
                if (next.equals(map)) return; // nothing to queue
                changed = changedKeys(map, next);
                map = next;
                snapshot = new HashMap<>(map);
            }
            final Map<String, Object> toWrite = snapshot;
            final Set<String> toNotify = changed;
            IsakaiPrefsWork.enqueue(() -> {
                writeToDisk(toWrite);
                for (String k : toNotify) notifyListeners(k);
            });
        }

        private Set<String> changedKeys(Map<String, Object> before, Map<String, Object> after) {
            Set<String> keys = new HashSet<>(before.keySet());
            keys.addAll(after.keySet());
            keys.removeIf(k -> java.util.Objects.equals(before.get(k), after.get(k)));
            return keys;
        }
    }

    @Override
    public String toString() {
        return "SharedPreferencesImpl{" + file + "}";
    }

    /** Test-only: read the raw XML for schema assertions. */
    String rawXmlForTest() throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
