// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Resources: system DisplayMetrics plus configuration snapshots, backed by a
 * staged resource table rooted at the staged code dir ({@code -Disakai.codeDir}).
 *
 * <p>Host side ({@code src/install.zig} + {@code src/arsc.zig}) parses
 * {@code resources.arsc} at install time; this class loads the staged copy at
 * runtime: {@code <codeDir>/resources.arsc}. The table format is shared with
 * the Zig reader (string pool, packages, type chunks, entry index, Res_value);
 * this parser is intentionally narrow — default config wins, first present
 * qualified chunk is the fallback, bags are skipped, {@code @ref} chains
 * resolve with a depth cap. Anything unexpected throws
 * {@link NotFoundException} (fail-closed, never a guessed value).
 *
 * <p>When no staged table exists (system resources, unit tests), every id
 * lookup throws {@link NotFoundException} as before.
 */
public class Resources {
    private final DisplayMetrics metrics = new DisplayMetrics();
    private final Configuration configuration = new Configuration();
    private final Table table;

    public Resources() {
        this.table = Table.loadStaged();
        // Headless default: portrait (real displays always report an
        // orientation; undefined breaks app branching).
        this.configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
    }

    /** Copy constructor for system snapshots. */
    public Resources(DisplayMetrics seed) {
        this.table = Table.loadStaged();
        setMetrics(seed.widthPixels, seed.heightPixels, seed.densityDpi,
                seed.density, seed.scaledDensity, seed.xdpi, seed.ydpi);
    }

    /** Test hook: wrap an explicit table (null = no table). */
    Resources(Table table) {
        this.table = table;
    }

    public DisplayMetrics getDisplayMetrics() {
        return metrics;
    }

    /** Fill from an int[8]: w,h,densityDpi,density*1000,scaled*1000,xdpi*1000,ydpi*1000,scale(unused). */
    public void setMetrics(int w, int h, int densityDpi, float density, float scaled, float xdpi, float ydpi) {
        metrics.widthPixels = w;
        metrics.heightPixels = h;
        metrics.densityDpi = densityDpi;
        metrics.density = density;
        metrics.scaledDensity = scaled;
        metrics.xdpi = xdpi;
        metrics.ydpi = ydpi;
    }

    public Configuration getConfiguration() {
        return new Configuration(configuration);
    }

    public void updateConfiguration(Configuration config, DisplayMetrics dm) {
        if (config != null) configuration.setTo(config);
        if (dm != null) {
            setMetrics(dm.widthPixels, dm.heightPixels, dm.densityDpi,
                    dm.density, dm.scaledDensity, dm.xdpi, dm.ydpi);
        }
    }

    public AssetManager getAssets() {
        return new AssetManager();
    }

    private static NotFoundException missing(int id) {
        return new NotFoundException("no resource table: 0x" + Integer.toHexString(id));
    }

    private Table.Entry resolve(int id) {
        if (table == null) throw missing(id);
        Table.Entry e = table.lookup(id);
        if (e == null) throw missing(id);
        return e;
    }

    private Table.Entry resolveType(int id, int firstType, int lastType) {
        Table.Entry e = resolve(id);
        if (e.dataType < firstType || e.dataType > lastType) throw missing(id);
        return e;
    }

    public String getString(int id) {
        Table.Entry e = resolve(id);
        if (e.dataType != TypedValue.TYPE_STRING || e.string == null) throw missing(id);
        return e.string;
    }

    public CharSequence getText(int id) {
        return getString(id);
    }

    public String[] getStringArray(int id) {
        if (table == null) throw missing(id);
        String[] arr = table.lookupStringArray(id);
        if (arr == null) throw missing(id);
        return arr;
    }

    public CharSequence[] getTextArray(int id) {
        String[] arr = getStringArray(id);
        CharSequence[] out = new CharSequence[arr.length];
        System.arraycopy(arr, 0, out, 0, arr.length);
        return out;
    }

    public boolean getBoolean(int id) {
        Table.Entry e = resolveType(id, TypedValue.TYPE_INT_BOOLEAN, TypedValue.TYPE_INT_BOOLEAN);
        return e.data != 0;
    }

    public int getInteger(int id) {
        Table.Entry e = resolveType(id, TypedValue.TYPE_FIRST_INT, TypedValue.TYPE_LAST_INT);
        return e.data;
    }

    public float getDimension(int id) {
        Table.Entry e = resolveType(id, TypedValue.TYPE_DIMENSION, TypedValue.TYPE_DIMENSION);
        return TypedValue.complexToDimension(e.data, metrics);
    }

    public int getDimensionPixelSize(int id) {
        Table.Entry e = resolveType(id, TypedValue.TYPE_DIMENSION, TypedValue.TYPE_DIMENSION);
        return TypedValue.complexToDimensionPixelSize(e.data, metrics);
    }

    public int getDimensionPixelOffset(int id) {
        Table.Entry e = resolveType(id, TypedValue.TYPE_DIMENSION, TypedValue.TYPE_DIMENSION);
        return TypedValue.complexToDimensionPixelOffset(e.data, metrics);
    }

    public float getFraction(int id, int base, int pbase) {
        Table.Entry e = resolveType(id, TypedValue.TYPE_FRACTION, TypedValue.TYPE_FRACTION);
        TypedValue v = new TypedValue();
        v.type = e.dataType;
        v.data = e.data;
        return v.getFraction(base, pbase);
    }

    public int getColor(int id) {
        Table.Entry e = resolveType(id, TypedValue.TYPE_FIRST_COLOR_INT, TypedValue.TYPE_LAST_COLOR_INT);
        return e.data;
    }

    public ColorStateList getColorStateList(int id) {
        if (table == null) throw missing(id);
        // A color int backs a single-color list; otherwise the entry names a
        // staged XML file (res/*.xml) parsed as a <selector>.
        Table.Entry e = resolve(id);
        if (e.dataType >= TypedValue.TYPE_FIRST_COLOR_INT && e.dataType <= TypedValue.TYPE_LAST_COLOR_INT) {
            return ColorStateList.valueOf(e.data);
        }
        if (e.dataType == TypedValue.TYPE_STRING && e.string != null) {
            File f = table.stagedFile(e.string);
            if (f == null) throw missing(id);
            try (InputStream in = new FileInputStream(f)) {
                XmlPullParser parser = android.util.Xml.newPullParser();
                parser.setInput(in, "UTF-8");
                return ColorStateList.createFromXml(this, parser);
            } catch (IOException | XmlPullParserException ex) {
                throw missing(id);
            }
        }
        throw missing(id);
    }

    public Drawable getDrawable(int id) {
        if (table == null) throw missing(id);
        Table.Entry e = resolve(id);
        if (e.dataType >= TypedValue.TYPE_FIRST_COLOR_INT && e.dataType <= TypedValue.TYPE_LAST_COLOR_INT) {
            return new android.graphics.drawable.ColorDrawable(e.data);
        }
        if (e.dataType == TypedValue.TYPE_STRING && e.string != null) {
            File f = table.stagedFile(e.string);
            if (f == null) throw missing(id);
            String path = f.getPath();
            String lower = path.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".xml")) {
                // Binary-aware: staged res XML is compiled binary XML.
                try (XmlResourceParser parser = openXml(id)) {
                    Drawable d = Drawable.createFromXml(this, parser);
                    if (d != null) return d;
                } catch (RuntimeException ex) {
                    // Fall through to the bitmap path, then fail closed.
                }
            }
            android.graphics.Bitmap bitmap = null;
            try (InputStream in = new FileInputStream(f)) {
                bitmap = android.graphics.BitmapFactory.decodeStream(in);
            } catch (IOException | RuntimeException ex) {
                bitmap = null;
            }
            if (bitmap != null) return new android.graphics.drawable.BitmapDrawable(this, bitmap);
            throw missing(id);
        }
        if (e.dataType == TypedValue.TYPE_REFERENCE) throw missing(id);
        throw missing(id);
    }

    public void getValue(int id, TypedValue outValue, boolean resolveRefs) {
        if (outValue == null) throw new NullPointerException("outValue");
        if (table == null) throw missing(id);
        Table.Entry e = resolveRefs ? resolve(id) : table.lookupRaw(id);
        if (e == null) throw missing(id);
        e.fill(outValue, id);
    }

    public void getValue(String name, TypedValue outValue, boolean resolveRefs) {
        if (outValue == null) throw new NullPointerException("outValue");
        if (name == null || table == null) throw new NotFoundException("no resource table: " + name);
        int id = getIdentifier(name, null, null);
        if (id == 0) throw new NotFoundException("no resource table: " + name);
        getValue(id, outValue, resolveRefs);
    }

    /**
     * Typed value for an attribute id (android.R.attr / app attr). Non-null
     * only when the id names a value entry; the bag/map form of style
     * entries is not addressable this way (fail-closed null).
     */
    TypedValue attributeValue(int id) {
        if (table == null) return null;
        Table.Entry e = table.lookup(id);
        if (e == null) return null;
        TypedValue v = new TypedValue();
        e.fill(v, id);
        return v;
    }

    public XmlResourceParser getXml(int id) {
        return openXml(id);
    }

    public XmlResourceParser getLayout(int id) {
        return openXml(id);
    }

    public XmlResourceParser getAnimation(int id) {
        return openXml(id);
    }

    private XmlResourceParser openXml(int id) {
        if (table == null) throw missing(id);
        Table.Entry e = resolve(id);
        if (e.dataType != TypedValue.TYPE_STRING || e.string == null) throw missing(id);
        File f = table.stagedFile(e.string);
        if (f == null) throw missing(id);
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            return decodeBinaryXml(bytes, f.getPath());
        } catch (IOException ex) {
            throw missing(id);
        }
    }

    /**
     * Decode a binary Android XML file (layouts, menus, drawables) into a
     * text-XML pull parser. Reuses the staged string pool + resource map so
     * attribute resource ids resolve like the install-time reader. Plain-text
     * XML files decode through the same parser. Fail-closed: malformed input
     * throws NotFoundException, never a partial tree.
     */
    static XmlResourceParser decodeBinaryXml(byte[] bytes, String path) {
        String xml;
        if (bytes.length >= 8 && u16(bytes, 0) == 3 && u16(bytes, 2) == 8) {
            xml = BinaryXml.toText(bytes);
            if (xml == null) throw new NotFoundException("bad xml: " + path);
        } else {
            xml = new String(bytes, StandardCharsets.UTF_8);
        }
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLEventReader reader =
                    factory.createXMLEventReader(new java.io.StringReader(xml));
            return new PullParser(reader, path);
        } catch (XMLStreamException | XmlPullParserException ex) {
            throw new NotFoundException("bad xml: " + path);
        }
    }

    private static int u16(byte[] b, int off) {
        if (off + 2 > b.length) return -1;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    /** Minimal binary-XML (chunk 0x0003) to text-XML decoder. */
    static final class BinaryXml {
        static String toText(byte[] d) {
            try {
                return decode(d);
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static int s32(byte[] d, int off) {
            return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8)
                    | ((d[off + 2] & 0xFF) << 16) | ((d[off + 3] & 0xFF) << 24);
        }

        private static String poolString(byte[] d, int poolOff, int header, int start, boolean utf8, int idx) {
            int rel = s32(d, poolOff + header + idx * 4);
            int pos = poolOff + start + rel;
            if (utf8) {
                int[] p = {pos};
                int ulen = varLen(d, p);
                int blen = varLen(d, p);
                pos = p[0];
                if (pos + blen > d.length) throw new IllegalArgumentException("pool oob");
                return new String(d, pos, blen, StandardCharsets.UTF_8);
            }
            int chars = u16s(d, pos);
            pos += 2;
            if ((chars & 0x8000) != 0) {
                int lo = u16s(d, pos);
                pos += 2;
                chars = ((chars & 0x7FFF) << 16) | lo;
            }
            if (pos + chars * 2 > d.length) throw new IllegalArgumentException("pool oob");
            return new String(d, pos, chars * 2, StandardCharsets.UTF_16LE);
        }

        private static int varLen(byte[] d, int[] pos) {
            int first = d[pos[0]++] & 0xFF;
            if ((first & 0x80) == 0) return first;
            int second = d[pos[0]++] & 0xFF;
            return ((first & 0x7F) << 8) | second;
        }

        private static int u16s(byte[] d, int off) {
            return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8);
        }

        private static String decode(byte[] d) {
            if (u16(d, 0) != 3 || u16(d, 2) != 8 || s32(d, 4) != d.length) return null;
            int at = 8;
            int kind = u16(d, at);
            int header = u16(d, at + 2);
            int size = s32(d, at + 4);
            if (kind != 1 || header < 28) return null;
            int count = s32(d, at + 8);
            int flags = s32(d, at + 16);
            int start = s32(d, at + 20);
            if (count < 0 || count > 100000 || start < header + count * 4) return null;
            final boolean utf8 = (flags & 0x100) != 0;
            final int poolOff = at;
            final int poolHeader = header;
            final int poolStart = start;
            final int poolCount = count;
            java.util.function.IntFunction<String> str = idx -> {
                if (idx < 0 || idx >= poolCount) throw new IllegalArgumentException("bad string idx");
                return poolString(d, poolOff, poolHeader, poolStart, utf8, idx);
            };
            at += size;
            // Resource map (0x180): indexed by attribute NAME's string-pool
            // index (aapt2 sorts attr names first, ordered by id); the value
            // is the android.R.attr / app attr id. NOT parallel to any
            // element's attribute list.
            int[] resMap = new int[0];
            if (at + 8 <= d.length && u16(d, at) == 0x180) {
                int rh = u16(d, at + 2);
                int rs = s32(d, at + 4);
                int n = (rs - rh) / 4;
                if (n < 0 || at + rs > d.length) return null;
                resMap = new int[n];
                for (int i = 0; i < n; i++) resMap[i] = s32(d, at + rh + i * 4);
                at += rs;
            }
            StringBuilder sb = new StringBuilder("<inflate>");
            java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
            while (at + 8 <= d.length) {
                int k = u16(d, at);
                int h = u16(d, at + 2);
                int sz = s32(d, at + 4);
                if (h < 8 || sz < h || at + sz > d.length) return null;
                if (k == 0x100 || k == 0x101) {
                    // Namespace decls carry no element content; skipped.
                } else if (k == 0x102) {
                    if (h != 16) return null;
                    String name = str.apply(s32(d, at + 20));
                    int aStart = 16 + u16(d, at + 24);
                    int aStride = u16(d, at + 26);
                    int aCount = u16(d, at + 28);
                    if (aStride < 20 || aStart < 36 || aStart + aStride * aCount > sz) return null;
                    sb.append('<').append(esc(name));
                    for (int i = 0; i < aCount; i++) {
                        int ao = at + aStart + i * aStride;
                        int nsIdx = s32(d, ao);
                        int nmIdx = s32(d, ao + 4);
                        int raw = s32(d, ao + 8);
                        int typ = d[ao + 15] & 0xFF;
                        int val = s32(d, ao + 16);
                        String attrName = str.apply(nmIdx);
                        String value;
                        if (raw != -1) {
                            value = str.apply(raw);
                        } else if (typ == 3) {
                            value = str.apply(val);
                        } else if (typ == 1) {
                            value = "@0x" + Integer.toHexString(val);
                        } else if (typ >= 0x10 && typ <= 0x12) {
                            value = Integer.toString(val);
                        } else if (typ == 4) {
                            value = Float.toString(Float.intBitsToFloat(val));
                        } else if (typ >= 0x1c && typ <= 0x1f) {
                            value = String.format("#%08x", val);
                        } else if (typ == 5) {
                            value = dimText(val);
                        } else {
                            continue;
                        }
                        if (nsIdx != -1 && "http://schemas.android.com/apk/res/android".equals(str.apply(nsIdx))) {
                            // android: prefix; also stash the attr id when the
                            // resource map covers this slot.
                            sb.append(" android:").append(esc(attrName)).append("=\"").append(esc(value)).append('"');
                        } else {
                            sb.append(' ').append(esc(attrName)).append("=\"").append(esc(value)).append('"');
                        }
                        int attrId = (nmIdx >= 0 && nmIdx < resMap.length) ? resMap[nmIdx] : 0;
                        if (attrId != 0) {
                            // Keyed by attribute identity, not position: StAX
                            // does not report attributes in document order.
                            boolean isAndroid = nsIdx != -1
                                    && "http://schemas.android.com/apk/res/android".equals(str.apply(nsIdx));
                            sb.append(" data-isakai-attr-").append(i).append('-')
                                    .append(isAndroid ? 'a' : 'x').append('_')
                                    .append(esc(attrName)).append("=\"")
                                    .append(attrId).append(':').append(typ).append(':')
                                    .append(val & 0xFFFFFFFFL).append('"');
                        }
                    }
                    sb.append('>');
                    stack.push(name);
                } else if (k == 0x103) {
                    String name = str.apply(s32(d, at + 20));
                    if (stack.isEmpty() || !stack.pop().equals(name)) return null;
                    sb.append("</").append(esc(name)).append('>');
                } else {
                    return null;
                }
                at += sz;
            }
            if (!stack.isEmpty()) return null;
            sb.append("</inflate>");
            // Strip the synthetic wrapper; the wrapper root re-declares the
            // android namespace so binary XML's android: attributes (which
            // carry no xmlns decl of their own) parse under StAX.
            String inner = sb.substring("<inflate>".length(), sb.length() - "</inflate>".length());
            return "<root xmlns:android=\"http://schemas.android.com/apk/res/android\">" + inner + "</root>";
        }

        private static String dimText(int data) {
            // Same mantissa/radix packing as TypedValue.complexToFloat.
            float[] mults = {1.0f / (1 << 8), 1.0f / ((1 << 7) * (1 << 8)),
                1.0f / ((1 << 15) * (1 << 8)), 1.0f / ((1 << 23) * (1 << 8))};
            int radix = (data >> 4) & 0x3;
            float value = (float) (data & 0xFFFFFF00) * mults[radix];
            int unit = data & 0xF;
            String[] units = {"px", "dp", "sp", "pt", "in", "mm"};
            return value + units[unit < units.length ? unit : 0];
        }

        private static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }

    /** StAX-backed XmlResourceParser over decoded XML text. */
    static final class PullParser implements XmlResourceParser {
        private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

        private static final class Elem {
            final String name;
            int depth;
            final List<String> attrNames = new ArrayList<>();
            final List<String> attrValues = new ArrayList<>();
            final List<String> attrNs = new ArrayList<>();
            final List<Integer> attrIds = new ArrayList<>();
            final List<Integer> attrTypes = new ArrayList<>();
            final List<Integer> attrData = new ArrayList<>();
            final java.util.HashMap<String, int[]> synthetic = new java.util.HashMap<>();
            Elem(String name) { this.name = name; }
            /** Attach recorded resid/typ/val to the just-appended real attr. */
            void attachSynthetic(String ns, String local) {
                boolean isAndroid = "http://schemas.android.com/apk/res/android".equals(ns)
                        || "android".equals(ns);
                String ident = (isAndroid ? "a_" : "x_") + local;
                int[] rec = synthetic.remove(ident);
                if (rec == null) {
                    // Fallback: android attrs also match unqualified (binary
                    // XML sometimes omits the namespace index).
                    rec = synthetic.remove((isAndroid ? "x_" : "a_") + local);
                }
                if (rec == null) {
                    attrIds.add(0);
                    attrTypes.add(0);
                    attrData.add(0);
                } else {
                    attrIds.add(rec[0]);
                    attrTypes.add(rec[1]);
                    attrData.add(rec[2]);
                }
            }
        }

        private final String position;
        private final java.util.ArrayDeque<Elem> stack = new java.util.ArrayDeque<>();
        private final java.util.ArrayDeque<Integer> events = new java.util.ArrayDeque<>();
        private String text = "";
        private int event = START_DOCUMENT;
        private boolean closed;

        PullParser(XMLEventReader reader, String position) throws XmlPullParserException {
            this.position = position == null ? "<unknown>" : position;
            try {
                parseAll(reader);
            } catch (XMLStreamException e) {
                throw new XmlPullParserException("bad xml", this, e);
            }
        }

        private void parseAll(XMLEventReader reader) throws XMLStreamException {
            // Flatten to: START_DOCUMENT, then per element START_TAG/TEXT/END_TAG.
            events.add(START_DOCUMENT);
            java.util.ArrayDeque<Elem> open = new java.util.ArrayDeque<>();
            while (reader.hasNext()) {
                XMLEvent ev = reader.nextEvent();
                if (ev.isStartElement()) {
                    StartElement se = ev.asStartElement();
                    String name = se.getName().getLocalPart();
                    if ("root".equals(name) && open.isEmpty()) {
                        open.push(new Elem(name));
                        continue;
                    }
                    Elem e = new Elem(name);
                    e.depth = open.size();
                    java.util.ArrayList<Attribute> all = new java.util.ArrayList<>();
                    java.util.Iterator<?> it = se.getAttributes();
                    while (it.hasNext()) all.add((Attribute) it.next());
                    // Pass 1: stash synthetic resid/typ/val by attribute
                    // identity (StAX reports real attrs before/after their
                    // synthetic twins unpredictably).
                    for (Attribute a : all) {
                        String local = a.getName().getLocalPart();
                        if (!local.startsWith("data-isakai-attr-")) continue;
                        try {
                            String key = local.substring("data-isakai-attr-".length());
                            int dash = key.indexOf('-');
                            String ident = key.substring(dash + 1);
                            String[] parts = a.getValue().split(":");
                            if (parts.length == 3) {
                                e.synthetic.put(ident,
                                        new int[] {
                                            (int) Long.parseLong(parts[0]),
                                            Integer.parseInt(parts[1]),
                                            (int) Long.parseLong(parts[2])
                                        });
                            }
                        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                            // Malformed synthetic attr: no entry (fail-closed).
                        }
                    }
                    // Pass 2: real attrs in reported order, ids attached.
                    for (Attribute a : all) {
                        String local = a.getName().getLocalPart();
                        if (local.startsWith("data-isakai-attr-")) continue;
                        String ns = a.getName().getNamespaceURI();
                        String prefix = a.getName().getPrefix();
                        e.attrNames.add(("android".equals(prefix) || ANDROID_NS.equals(ns)) ? local : local);
                        e.attrValues.add(a.getValue());
                        e.attrNs.add(ns == null ? "" : ns);
                        e.attachSynthetic(ns == null ? "" : ns, local);
                    }
                    open.push(e);
                    stack.add(e);
                    events.add(START_TAG);
                } else if (ev.isEndElement()) {
                    String name = ev.asEndElement().getName().getLocalPart();
                    if ("root".equals(name) && open.size() == 1) {
                        open.pop();
                        continue;
                    }
                    if (!open.isEmpty()) open.pop();
                    Elem e = new Elem("/" + name);
                    e.depth = open.size();
                    stack.add(e);
                    events.add(END_TAG);
                } else if (ev.isCharacters()) {
                    String s = ev.asCharacters().getData();
                    if (s != null && !s.trim().isEmpty()) {
                        Elem e = new Elem("#text");
                        stack.add(e);
                        events.add(TEXT);
                    }
                }
            }
            events.add(END_DOCUMENT);
        }

        private int cursor = -1;
        private final java.util.ArrayList<Elem> flat = new java.util.ArrayList<>();

        private Elem at() {
            return cursor >= 0 && cursor < flat.size() ? flat.get(cursor) : null;
        }

        @Override
        public int getEventType() {
            return event;
        }

        @Override
        public int next() {
            if (flat.isEmpty() && !stack.isEmpty()) flat.addAll(stack);
            if (cursor + 1 < flat.size()) {
                cursor++;
                Elem e = flat.get(cursor);
                if (e.name.startsWith("/")) event = END_TAG;
                else if ("#text".equals(e.name)) event = TEXT;
                else event = START_TAG;
            } else {
                event = END_DOCUMENT;
            }
            return event;
        }

        @Override
        public int nextToken() {
            return next();
        }

        @Override
        public String getName() {
            Elem e = at();
            if (e == null || e.name.startsWith("/") || "#text".equals(e.name)) return null;
            return e.name;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public char[] getTextCharacters(int[] holder) {
            char[] c = text.toCharArray();
            holder[0] = 0;
            holder[1] = c.length;
            return c;
        }

        @Override
        public String getNamespace() {
            return "";
        }

        @Override
        public String getPrefix() {
            return "";
        }

        @Override
        public int getDepth() {
            Elem e = at();
            return e == null ? 0 : e.depth;
        }

        @Override
        public String getPositionDescription() {
            return position;
        }

        @Override
        public int getLineNumber() {
            return -1;
        }

        @Override
        public int getColumnNumber() {
            return -1;
        }

        @Override
        public boolean isWhitespace() {
            return event == TEXT && (text == null || text.trim().isEmpty());
        }

        @Override
        public boolean isEmptyElementTag() {
            return false;
        }

        @Override
        public int getAttributeCount() {
            Elem e = at();
            return e == null ? 0 : e.attrNames.size();
        }

        @Override
        public String getAttributeNamespace(int index) {
            Elem e = at();
            if (e == null || index < 0 || index >= e.attrNs.size()) return "";
            return e.attrNs.get(index);
        }

        @Override
        public String getAttributeName(int index) {
            Elem e = at();
            if (e == null || index < 0 || index >= e.attrNames.size()) return "";
            return e.attrNames.get(index);
        }

        @Override
        public String getAttributePrefix(int index) {
            String ns = getAttributeNamespace(index);
            return ANDROID_NS.equals(ns) ? "android" : "";
        }

        @Override
        public String getAttributeType(int index) {
            return "CDATA";
        }

        @Override
        public boolean isAttributeDefault(int index) {
            return false;
        }

        @Override
        public String getAttributeValue(int index) {
            Elem e = at();
            if (e == null || index < 0 || index >= e.attrValues.size()) return null;
            return e.attrValues.get(index);
        }

        @Override
        public String getAttributeValue(String namespace, String name) {
            Elem e = at();
            if (e == null) return null;
            for (int i = 0; i < e.attrNames.size(); i++) {
                if (!e.attrNames.get(i).equals(name)) continue;
                String ns = e.attrNs.get(i);
                if (namespace == null || namespace.isEmpty() || namespace.equals("")) {
                    if (ns == null || ns.isEmpty()) return e.attrValues.get(i);
                } else if (namespace.equals(ns)
                        || (ANDROID_NS.equals(namespace) && ("android".equals(ns) || ANDROID_NS.equals(ns)))) {
                    return e.attrValues.get(i);
                }
            }
            // Unqualified fallback for android: attrs read without a namespace.
            for (int i = 0; i < e.attrNames.size(); i++) {
                if (e.attrNames.get(i).equals(name)) return e.attrValues.get(i);
            }
            return null;
        }

        @Override
        public int getAttributeNameResource(int index) {
            Elem e = at();
            if (e == null || index < 0 || index >= e.attrIds.size()) return 0;
            return e.attrIds.get(index);
        }

        /** Raw binary-XML type/data for the index-th attribute (0/0 when absent). */
        public int[] getAttributeRawValue(int index) {
            Elem e = at();
            if (e == null || index < 0 || index >= e.attrTypes.size()) return new int[] {0, 0};
            return new int[] {e.attrTypes.get(index), e.attrData.get(index)};
        }

        @Override
        public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) {
            return defaultValue;
        }

        @Override
        public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            return v == null ? defaultValue : Boolean.parseBoolean(v.trim());
        }

        @Override
        public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            if (v == null) return defaultValue;
            v = v.trim();
            if (v.startsWith("@0x") || v.startsWith("@")) {
                try {
                    return (int) Long.parseLong(v.substring(v.startsWith("@0x") ? 3 : 1), 16);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }

        @Override
        public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            if (v == null) return defaultValue;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        @Override
        public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) {
            return getAttributeIntValue(namespace, attribute, defaultValue);
        }

        @Override
        public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
            String v = getAttributeValue(namespace, attribute);
            if (v == null) return defaultValue;
            try {
                return Float.parseFloat(v.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        @Override
        public int getStyleAttribute() {
            return 0;
        }

        @Override
        public void recycle() {}

        @Override
        public void setFeature(String name, boolean state) {}

        @Override
        public boolean getFeature(String name) {
            return false;
        }

        @Override
        public void setProperty(String name, Object value) {}

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public void setInput(java.io.InputStream in, String enc) {}

        @Override
        public void setInput(java.io.Reader in) {}

        @Override
        public String getInputEncoding() {
            return "UTF-8";
        }

        @Override
        public void defineEntityReplacementText(String entity, String text) {}

        @Override
        public int getNamespaceCount(int depth) {
            return 0;
        }

        @Override
        public String getNamespacePrefix(int pos) {
            return "";
        }

        @Override
        public String getNamespaceUri(int pos) {
            return "";
        }

        @Override
        public String getNamespace(String prefix) {
            return "android".equals(prefix) ? ANDROID_NS : "";
        }

        @Override
        public void require(int type, String namespace, String name) throws XmlPullParserException, IOException {
            if (type != event) throw new XmlPullParserException("expected " + type + " got " + event);
        }

        @Override
        public String nextText() throws XmlPullParserException, IOException {
            if (event != START_TAG) throw new XmlPullParserException("not on START_TAG");
            int t = next();
            if (t == TEXT) {
                String s = text;
                next();
                return s;
            }
            return "";
        }

        @Override
        public int nextTag() throws XmlPullParserException, IOException {
            int t = next();
            while (t == TEXT && isWhitespace()) t = next();
            if (t != START_TAG && t != END_TAG) throw new XmlPullParserException("expected tag");
            return t;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    public String getResourceName(int id) {
        if (table == null) throw missing(id);
        String n = table.resourceName(id);
        if (n == null) throw missing(id);
        return n;
    }

    public String getResourceEntryName(int id) {
        if (table == null) throw missing(id);
        String n = table.entryName(id);
        if (n == null) throw missing(id);
        return n;
    }

    public String getResourceTypeName(int id) {
        if (table == null) throw missing(id);
        String n = table.typeName(id);
        if (n == null) throw missing(id);
        return n;
    }

    public String getResourcePackageName(int id) {
        if (table == null) throw missing(id);
        String n = table.packageName(id);
        if (n == null) throw missing(id);
        return n;
    }

    public int getIdentifier(String name, String defType, String defPackage) {
        if (table == null || name == null) return 0;
        return table.identifier(name, defType, defPackage);
    }

    public TypedArray obtainAttributes(android.util.AttributeSet set, int[] attrs) {
        return new TypedArray(this, set, attrs);
    }

    private static final Resources SYSTEM = new Resources();

    static {
        SYSTEM.setMetrics(640, 480, 160, 1.0f, 1.0f, 160.0f, 160.0f);
    }

    public static Resources getSystem() {
        return SYSTEM;
    }

    public Theme newTheme() {
        return new Theme(this);
    }

    /** Theme handle: style overlays resolve first, table values second. */
    public static class Theme {
        private final Resources resources;
        private final java.util.LinkedHashMap<Integer, Table.Entry> overlay =
                new java.util.LinkedHashMap<>();

        Theme(Resources resources) {
            this.resources = resources;
        }

        public void applyStyle(int resId, boolean force) {
            if (resources == null || resources.table == null || resId == 0) return;
            java.util.LinkedHashMap<Integer, Table.Entry> merged = new java.util.LinkedHashMap<>();
            collectStyle(resId, merged, 0);
            if (force) {
                overlay.putAll(merged);
            } else {
                for (java.util.Map.Entry<Integer, Table.Entry> e : merged.entrySet()) {
                    overlay.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        /** Merge parent-first so child items win; cycle/depth capped. */
        private void collectStyle(int styleId, java.util.LinkedHashMap<Integer, Table.Entry> out, int depth) {
            if (depth > 16 || resources == null || resources.table == null) return;
            Table.Bag bag = resources.table.readBag(styleId);
            if (bag == null) return;
            int parent = bag.parent;
            if (parent == 0) {
                parent = implicitParent(styleId);
            }
            if (parent != 0 && parent != styleId) {
                collectStyle(parent, out, depth + 1);
            }
            out.putAll(bag.items);
        }

        /** Implicit parent: strip the last '.'-separated style name segment. */
        private int implicitParent(int styleId) {
            String name;
            try {
                name = resources.getResourceEntryName(styleId);
            } catch (RuntimeException e) {
                return 0;
            }
            int dot = name.lastIndexOf('.');
            if (dot <= 0) return 0;
            String pkg;
            try {
                String full = resources.getResourceName(styleId);
                int colon = full.indexOf(':');
                pkg = colon < 0 ? null : full.substring(0, colon);
            } catch (RuntimeException e) {
                pkg = null;
            }
            return resources.table.styleIdByName(pkg, name.substring(0, dot));
        }

        public void setTo(Theme other) {
            overlay.clear();
            if (other != null) overlay.putAll(other.overlay);
        }

        /** Theme-first attribute lookup for styled arrays. */
        TypedValue themeValue(int attrId) {
            Table.Entry e = overlay.get(attrId);
            if (e == null) return null;
            TypedValue v = new TypedValue();
            e.fill(v, attrId);
            return v;
        }

        public TypedArray obtainStyledAttributes(int[] attrs) {
            return new TypedArray(resources, this, null, attrs);
        }

        public TypedArray obtainStyledAttributes(int resid, int[] attrs) {
            Theme t = new Theme(resources);
            t.applyStyle(resid, true);
            t.overlay.putAll(overlay);
            Theme merged = new Theme(resources);
            merged.overlay.putAll(t.overlay);
            return new TypedArray(resources, merged, null, attrs);
        }

        public TypedArray obtainStyledAttributes(android.util.AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
            if ((defStyleAttr != 0 || defStyleRes != 0) && resources != null && resources.table != null) {
                // defStyleAttr/defStyleRes layer under the tag literals.
                Theme t = new Theme(resources);
                if (defStyleAttr != 0) {
                    TypedValue v = themeValue(defStyleAttr);
                    if (v == null) v = resources.attributeValue(defStyleAttr);
                    if (v != null && v.resourceId != 0) t.applyStyle(v.resourceId, true);
                    else if (v != null && v.type == TypedValue.TYPE_REFERENCE) t.applyStyle(v.data, true);
                }
                if (defStyleRes != 0) t.applyStyle(defStyleRes, true);
                t.overlay.putAll(overlay);
                Theme merged = new Theme(resources);
                merged.overlay.putAll(t.overlay);
                return new TypedArray(resources, merged, set, attrs);
            }
            return new TypedArray(resources, this, set, attrs);
        }

        public boolean resolveAttribute(int resid, TypedValue outValue, boolean resolveRefs) {
            if (outValue == null || resources == null || resid == 0) return false;
            // Theme overlay first (applied styles), then the raw table.
            // Follows ?attr/ chains through the theme (cap 8, fail-closed).
            int id = resid;
            for (int depth = 0; depth < 8; depth++) {
                TypedValue v = themeValue(id);
                if (v == null) v = resources.attributeValue(id);
                if (v == null) return false;
                if (!resolveRefs || v.type != TypedValue.TYPE_ATTRIBUTE || v.data == 0 || v.data == id) {
                    outValue.type = v.type;
                    outValue.string = v.string;
                    outValue.data = v.data;
                    outValue.assetCookie = v.assetCookie;
                    outValue.resourceId = v.resourceId != 0 ? v.resourceId : id;
                    outValue.changingConfigurations = v.changingConfigurations;
                    outValue.density = v.density;
                    return true;
                }
                id = v.data;
            }
            return false;
        }


        public Resources getResources() {
            return resources;
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Parsed resources.arsc: global string pool, packages (type/key names),
     * type chunks with config blobs, entry index. Mirrors src/arsc.zig so
     * host and runtime agree on every covered path.
     */
    static final class Table {
        final byte[] bytes;
        final String[] globals;
        final List<Pkg> packages = new ArrayList<>();
        final File codeDir;

        static final class Entry {
            int dataType;
            int data;
            String string;
            // First reference id followed to reach this value (0 direct).
            int resourceId;
            void fill(TypedValue v, int id) {
                v.type = dataType;
                v.data = data;
                v.string = string;
                v.resourceId = resourceId != 0 ? resourceId : 0;
                v.assetCookie = 0;
                v.changingConfigurations = 0;
                v.density = 0;
            }
        }

        static final class TypeChunk {
            int off;
            int header;
            int id;
            int entryCount;
            int entriesStart;
            boolean defaultConfig;
        }

        static final class Pkg {
            int id;
            String name;
            String[] typeNames;
            String[] keyNames;
            final List<TypeChunk> types = new ArrayList<>();
        }

        Table(byte[] bytes, String[] globals, File codeDir) {
            this.bytes = bytes;
            this.globals = globals;
            this.codeDir = codeDir;
        }

        static Table loadStaged() {
            String dir = System.getProperty("isakai.codeDir", "");
            if (dir == null || dir.isEmpty()) return null;
            File arsc = new File(new File(dir), "resources.arsc");
            if (!arsc.isFile()) return null;
            try {
                byte[] bytes = Files.readAllBytes(arsc.toPath());
                if (bytes.length > 16 * 1024 * 1024) return null;
                return parse(bytes, new File(dir));
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        static int u16(byte[] d, int off) {
            if (off + 2 > d.length) throw new IllegalArgumentException("truncated");
            return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8);
        }

        static int s32(byte[] d, int off) {
            if (off + 4 > d.length) throw new IllegalArgumentException("truncated");
            return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8)
                    | ((d[off + 2] & 0xFF) << 16) | ((d[off + 3] & 0xFF) << 24);
        }

        static int varLen(byte[] d, int[] pos) {
            int first = d[pos[0]++] & 0xFF;
            if ((first & 0x80) == 0) return first;
            int second = d[pos[0]++] & 0xFF;
            return ((first & 0x7F) << 8) | second;
        }

        static String[] decodePool(byte[] d, int off) {
            int kind = u16(d, off);
            int header = u16(d, off + 2);
            int size = s32(d, off + 4);
            if (kind != 1 || header < 28) throw new IllegalArgumentException("bad pool");
            int count = s32(d, off + 8);
            int flags = s32(d, off + 16);
            int start = s32(d, off + 20);
            if (count < 0 || count > 200000 || start < header + count * 4) {
                throw new IllegalArgumentException("bad pool");
            }
            boolean utf8 = (flags & 0x100) != 0;
            String[] out = new String[count];
            for (int i = 0; i < count; i++) {
                int rel = s32(d, off + header + i * 4);
                int pos = off + start + rel;
                if (utf8) {
                    int[] p = {pos};
                    int ulen = varLen(d, p);
                    int blen = varLen(d, p);
                    pos = p[0];
                    if (pos + blen > d.length) throw new IllegalArgumentException("pool oob");
                    out[i] = new String(d, pos, blen, StandardCharsets.UTF_8);
                } else {
                    int chars = u16(d, pos);
                    pos += 2;
                    if ((chars & 0x8000) != 0) {
                        int lo = u16(d, pos);
                        pos += 2;
                        chars = ((chars & 0x7FFF) << 16) | lo;
                    }
                    if (pos + chars * 2 > d.length) throw new IllegalArgumentException("pool oob");
                    out[i] = new String(d, pos, chars * 2, StandardCharsets.UTF_16LE);
                }
            }
            return out;
        }

        static boolean isDefaultConfig(byte[] d, int off, int len) {
            if (len < 4 || off + len > d.length) return false;
            for (int i = off + 4; i < off + len; i++) {
                if (d[i] != 0) return false;
            }
            return true;
        }

        static Table parse(byte[] bytes, File codeDir) {
            int kind = u16(bytes, 0);
            int header = u16(bytes, 2);
            int size = s32(bytes, 4);
            if (kind != 2 || header < 12 || size != bytes.length) {
                throw new IllegalArgumentException("bad table");
            }
            int pkgCount = s32(bytes, 8);
            if (pkgCount < 0 || pkgCount > 16) throw new IllegalArgumentException("bad pkg count");
            int at = header;
            String[] globals = decodePool(bytes, at);
            int poolSize = s32(bytes, at + 4);
            at += poolSize;
            Table t = new Table(bytes, globals, codeDir);
            for (int p = 0; p < pkgCount; p++) {
                int pk = u16(bytes, at);
                int ph = u16(bytes, at + 2);
                int ps = s32(bytes, at + 4);
                if (pk != 0x200 || ph < 288 || at + ps > bytes.length) {
                    throw new IllegalArgumentException("bad package");
                }
                Pkg pkg = new Pkg();
                pkg.id = s32(bytes, at + 8);
                StringBuilder name = new StringBuilder();
                for (int i = 0; i < 128; i++) {
                    int ch = u16(bytes, at + 12 + i * 2);
                    if (ch == 0) break;
                    name.append((char) ch);
                }
                pkg.name = name.toString();
                int typeRel = s32(bytes, at + 268);
                int keyRel = s32(bytes, at + 276);
                if (typeRel < 0 || keyRel < 0 || typeRel >= ps || keyRel >= ps) {
                    throw new IllegalArgumentException("bad pool offsets");
                }
                pkg.typeNames = decodePool(bytes, at + typeRel);
                pkg.keyNames = decodePool(bytes, at + keyRel);
                int co = at + ph;
                int end = at + ps;
                while (co < end) {
                    int ck = u16(bytes, co);
                    int ch = u16(bytes, co + 2);
                    int cs = s32(bytes, co + 4);
                    if (ch < 8 || cs < ch || co + cs > end) throw new IllegalArgumentException("bad chunk");
                    if (ck == 0x202 || ck == 1) {
                        // Masks only; nested string pools (type/key names
                        // live at typeRel/keyRel) carry no entries: skip.
                    } else if (ck == 0x201) {
                        if (ch < 20) throw new IllegalArgumentException("bad type");
                        TypeChunk tc = new TypeChunk();
                        tc.off = co;
                        tc.header = ch;
                        tc.id = bytes[co + 8] & 0xFF;
                        tc.entryCount = s32(bytes, co + 12);
                        tc.entriesStart = s32(bytes, co + 16);
                        if (tc.entryCount < 0 || tc.entryCount > 200000) {
                            throw new IllegalArgumentException("bad entries");
                        }
                        tc.defaultConfig = isDefaultConfig(bytes, co + 20, ch - 20);
                        if ((long) co + ch + (long) tc.entryCount * 4 > co + cs) {
                            throw new IllegalArgumentException("bad index");
                        }
                        if (tc.entriesStart < 0 || tc.entriesStart > cs) {
                            throw new IllegalArgumentException("bad entries start");
                        }
                        pkg.types.add(tc);
                    } else {
                        throw new IllegalArgumentException("bad package chunk " + ck);
                    }
                    co += cs;
                }
                t.packages.add(pkg);
                at += ps;
            }
            return t;
        }

        Entry readEntry(TypeChunk tc, int rel, int depth) {
            int at = tc.off + tc.entriesStart + rel;
            int flags = u16(bytes, at + 2);
            if ((flags & 1) != 0) return null;
            if (at + 16 > bytes.length) return null;
            int dtype = bytes[at + 11] & 0xFF;
            int data = s32(bytes, at + 12);
            if (dtype == TypedValue.TYPE_REFERENCE) {
                if (depth >= 8) return null;
                Entry r = lookupDepth(data, depth + 1);
                if (r != null && r.resourceId == 0) r.resourceId = data;
                return r;
            }
            Entry e = new Entry();
            e.dataType = dtype;
            e.data = data;
            if (dtype == TypedValue.TYPE_STRING) {
                if (data < 0 || data >= globals.length) return null;
                e.string = globals[data];
            }
            return e;
        }

        Entry lookupDepth(int id, int depth) {
            if (depth > 8) return null;
            int pkgId = (id >>> 24) & 0xFF;
            int typeId = (id >>> 16) & 0xFF;
            int entryIdx = id & 0xFFFF;
            for (Pkg pkg : packages) {
                if (pkg.id != pkgId) continue;
                TypeChunk fallback = null;
                for (TypeChunk tc : pkg.types) {
                    if (tc.id != typeId || entryIdx >= tc.entryCount) continue;
                    int rel = s32(bytes, tc.off + tc.header + entryIdx * 4);
                    if (rel == -1) continue;
                    if (tc.defaultConfig) return readEntry(tc, rel, depth);
                    if (fallback == null) fallback = tc;
                }
                if (fallback != null) {
                    int rel = s32(bytes, fallback.off + fallback.header + entryIdx * 4);
                    if (rel == -1) return null;
                    return readEntry(fallback, rel, depth);
                }
                return null;
            }
            return null;
        }

        Entry lookup(int id) {
            return lookupDepth(id, 0);
        }

        Entry lookupRaw(int id) {
            Entry e = lookupDepth(id, 0);
            if (e != null && e.dataType == TypedValue.TYPE_REFERENCE) {
                // Unresolved form: keep the reference itself.
                Entry r = new Entry();
                r.dataType = TypedValue.TYPE_REFERENCE;
                r.data = e.data;
                return r;
            }
            return e;
        }

        String[] lookupStringArray(int id) {
            int pkgId = (id >>> 24) & 0xFF;
            int typeId = (id >>> 16) & 0xFF;
            int entryIdx = id & 0xFFFF;
            for (Pkg pkg : packages) {
                if (pkg.id != pkgId) continue;
                for (TypeChunk tc : pkg.types) {
                    if (tc.id != typeId || entryIdx >= tc.entryCount) continue;
                    int rel = s32(bytes, tc.off + tc.header + entryIdx * 4);
                    if (rel == -1) continue;
                    int at = tc.off + tc.entriesStart + rel;
                    int flags = u16(bytes, at + 2);
                    if ((flags & 1) == 0) return null;
                    // Bag: parent u32, count u32, then count x (name, value).
                    int count = s32(bytes, at + 12);
                    if (count < 0 || count > 10000) return null;
                    String[] out = new String[count];
                    int mp = at + 16;
                    for (int i = 0; i < count; i++) {
                        int dtype = bytes[mp + 7] & 0xFF;
                        int data = s32(bytes, mp + 8);
                        Entry item;
                        if (dtype == TypedValue.TYPE_REFERENCE) {
                            item = lookupDepth(data, 1);
                        } else {
                            item = new Entry();
                            item.dataType = dtype;
                            item.data = data;
                            if (dtype == TypedValue.TYPE_STRING) {
                                if (data < 0 || data >= globals.length) return null;
                                item.string = globals[data];
                            }
                        }
                        if (item == null || item.dataType != TypedValue.TYPE_STRING || item.string == null) {
                            return null;
                        }
                        out[i] = item.string;
                        mp += 12;
                    }
                    if (!tc.defaultConfig) {
                        // Prefer a default-config bag when one exists.
                        String[] def = lookupDefaultBag(pkg, typeId, entryIdx);
                        if (def != null) return def;
                    }
                    return out;
                }
            }
            return null;
        }

        private String[] lookupDefaultBag(Pkg pkg, int typeId, int entryIdx) {
            for (TypeChunk tc : pkg.types) {
                if (tc.id != typeId || !tc.defaultConfig || entryIdx >= tc.entryCount) continue;
                int rel = s32(bytes, tc.off + tc.header + entryIdx * 4);
                if (rel == -1) return null;
                // Re-enter through the normal path by temporarily treating
                // this chunk as the match: read directly.
                int at = tc.off + tc.entriesStart + rel;
                int flags = u16(bytes, at + 2);
                if ((flags & 1) == 0) return null;
                int count = s32(bytes, at + 12);
                if (count < 0 || count > 10000) return null;
                String[] out = new String[count];
                int mp = at + 16;
                for (int i = 0; i < count; i++) {
                    int dtype = bytes[mp + 7] & 0xFF;
                    int data = s32(bytes, mp + 8);
                    Entry item = dtype == TypedValue.TYPE_REFERENCE
                            ? lookupDepth(data, 1) : simple(dtype, data);
                    if (item == null || item.dataType != TypedValue.TYPE_STRING || item.string == null) return null;
                    out[i] = item.string;
                    mp += 12;
                }
                return out;
            }
            return null;
        }

        /** Style bag: parent style id + ordered attr entries. Null when not a bag. */
        static final class Bag {
            int parent;
            final java.util.LinkedHashMap<Integer, Entry> items = new java.util.LinkedHashMap<>();
        }

        Bag readBag(int id) {
            int pkgId = (id >>> 24) & 0xFF;
            int typeId = (id >>> 16) & 0xFF;
            int entryIdx = id & 0xFFFF;
            for (Pkg pkg : packages) {
                if (pkg.id != pkgId) continue;
                TypeChunk match = null;
                for (TypeChunk tc : pkg.types) {
                    if (tc.id != typeId || entryIdx >= tc.entryCount) continue;
                    int rel = s32(bytes, tc.off + tc.header + entryIdx * 4);
                    if (rel == -1) continue;
                    if (tc.defaultConfig) {
                        match = tc;
                        break;
                    }
                    if (match == null) match = tc;
                }
                if (match == null) return null;
                int rel = s32(bytes, match.off + match.header + entryIdx * 4);
                int at = match.off + match.entriesStart + rel;
                int flags = u16(bytes, at + 2);
                if ((flags & 1) == 0) return null;
                int count = s32(bytes, at + 12);
                if (count < 0 || count > 10000) return null;
                Bag bag = new Bag();
                bag.parent = s32(bytes, at + 8);
                int mp = at + 16;
                for (int i = 0; i < count; i++) {
                    int nameId = s32(bytes, mp);
                    int dtype = bytes[mp + 7] & 0xFF;
                    int data = s32(bytes, mp + 8);
                    Entry item;
                    if (dtype == TypedValue.TYPE_REFERENCE) {
                        item = lookupDepth(data, 1);
                        if (item == null) {
                            mp += 12;
                            continue;
                        }
                        if (item.resourceId == 0) item.resourceId = data;
                    } else {
                        item = simple(dtype, data);
                        if (item == null) {
                            mp += 12;
                            continue;
                        }
                    }
                    bag.items.put(nameId, item);
                    mp += 12;
                }
                return bag;
            }
            return null;
        }

        /** Style id by qualified entry name (implicit parent lookup). */
        int styleIdByName(String packageName, String entryName) {
            for (Pkg pkg : packages) {
                if (packageName != null && !packageName.equals(pkg.name)) continue;
                for (TypeChunk tc : pkg.types) {
                    if (tc.id >= pkg.typeNames.length) continue;
                    if (!"style".equals(pkg.typeNames[tc.id])) continue;
                    for (int e = 0; e < tc.entryCount; e++) {
                        int rel = s32(bytes, tc.off + tc.header + e * 4);
                        if (rel == -1) continue;
                        int at = tc.off + tc.entriesStart + rel;
                        int keyIdx = s32(bytes, at);
                        if (keyIdx < 0 || keyIdx >= pkg.keyNames.length) continue;
                        if (entryName.equals(pkg.keyNames[keyIdx])) {
                            return (pkg.id << 24) | (tc.id << 16) | e;
                        }
                    }
                }
            }
            return 0;
        }

        private Entry simple(int dtype, int data) {
            Entry e = new Entry();
            e.dataType = dtype;
            e.data = data;
            if (dtype == TypedValue.TYPE_STRING) {
                if (data < 0 || data >= globals.length) return null;
                e.string = globals[data];
            }
            return e;
        }

        File stagedFile(String name) {
            if (name == null || codeDir == null) return null;
            if (name.contains("..") || name.startsWith("/")) return null;
            File f = new File(codeDir, name);
            try {
                String canon = f.getCanonicalPath();
                String base = codeDir.getCanonicalPath();
                if (!canon.equals(base) && !canon.startsWith(base + File.separator)) return null;
            } catch (IOException e) {
                return null;
            }
            return f.isFile() ? f : null;
        }

        String typeName(int id) {
            int pkgId = (id >>> 24) & 0xFF;
            int typeId = (id >>> 16) & 0xFF;
            for (Pkg pkg : packages) {
                if (pkg.id != pkgId) continue;
                if (typeId >= 1 && typeId <= pkg.typeNames.length) return pkg.typeNames[typeId - 1];
                return null;
            }
            return null;
        }

        String entryName(int id) {
            BagKey k = bagKey(id);
            if (k == null || k.keyIndex < 0 || k.keyIndex >= k.pkg.keyNames.length) return null;
            return k.pkg.keyNames[k.keyIndex];
        }

        String packageName(int id) {
            int pkgId = (id >>> 24) & 0xFF;
            for (Pkg pkg : packages) {
                if (pkg.id != pkgId) continue;
                return pkg.name;
            }
            return null;
        }

        String resourceName(int id) {
            String pkg = packageName(id);
            String type = typeName(id);
            String entry = entryName(id);
            if (pkg == null || type == null || entry == null) return null;
            return pkg + ":" + type + "/" + entry;
        }

        private static final class BagKey {
            Pkg pkg;
            int keyIndex;
        }

        private BagKey bagKey(int id) {
            int pkgId = (id >>> 24) & 0xFF;
            int typeId = (id >>> 16) & 0xFF;
            int entryIdx = id & 0xFFFF;
            for (Pkg pkg : packages) {
                if (pkg.id != pkgId) continue;
                for (TypeChunk tc : pkg.types) {
                    if (tc.id != typeId || entryIdx >= tc.entryCount) continue;
                    int rel = s32(bytes, tc.off + tc.header + entryIdx * 4);
                    if (rel == -1) continue;
                    int at = tc.off + tc.entriesStart + rel;
                    int key = s32(bytes, at + 4);
                    BagKey k = new BagKey();
                    k.pkg = pkg;
                    k.keyIndex = key;
                    return k;
                }
            }
            return null;
        }

        int identifier(String name, String defType, String defPackage) {
            if (name == null) return 0;
            String ref = name.startsWith("@") ? name.substring(1) : name;
            if (ref.startsWith("+")) ref = ref.substring(1);
            if (ref.startsWith("?")) return 0;
            String pkgName = defPackage;
            String type = defType;
            String entry = ref;
            int colon = ref.indexOf(':');
            if (colon >= 0) {
                pkgName = ref.substring(0, colon);
                entry = ref.substring(colon + 1);
            }
            int slash = entry.indexOf('/');
            if (slash >= 0) {
                type = entry.substring(0, slash);
                entry = entry.substring(slash + 1);
            }
            // Bare "@0x..." / "@123" numeric ids.
            if ((type == null || type.isEmpty()) && entry.matches("0[xX][0-9a-fA-F]+|[0-9]+")) {
                try {
                    return entry.startsWith("0x") || entry.startsWith("0X")
                            ? (int) Long.parseLong(entry.substring(2), 16)
                            : Integer.parseInt(entry);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            if (type == null || type.isEmpty() || entry.isEmpty()) return 0;
            for (Pkg pkg : packages) {
                if (pkgName != null && !pkgName.isEmpty() && !pkgName.equals(pkg.name)) continue;
                int typeId = -1;
                for (int i = 0; i < pkg.typeNames.length; i++) {
                    if (type.equals(pkg.typeNames[i])) {
                        typeId = i + 1;
                        break;
                    }
                }
                if (typeId < 0) continue;
                int keyIdx = -1;
                for (int i = 0; i < pkg.keyNames.length; i++) {
                    if (entry.equals(pkg.keyNames[i])) {
                        keyIdx = i;
                        break;
                    }
                }
                if (keyIdx < 0) continue;
                for (TypeChunk tc : pkg.types) {
                    if (tc.id != typeId) continue;
                    for (int ei = 0; ei < tc.entryCount; ei++) {
                        int rel = s32(bytes, tc.off + tc.header + ei * 4);
                        if (rel == -1) continue;
                        int at = tc.off + tc.entriesStart + rel;
                        if (s32(bytes, at + 4) == keyIdx) {
                            return (pkg.id << 24) | (typeId << 16) | ei;
                        }
                    }
                }
                // Name matches but no entry present: not found.
                if (pkgName != null && !pkgName.isEmpty()) return 0;
            }
            return 0;
        }
    }
}
