// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.util.TypedValue;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParserException;

/**
 * XmlResourceParser over a native ResXMLParser (AOSP XmlBlock.Parser).
 * Attribute ids, typed values and string-pool references come straight from
 * the compiled document, so TypedArray styling can hand this parser to
 * AOSP ApplyStyle. Closing the parser releases its XML block.
 */
final class ResXmlParser implements XmlResourceParser {
    private static final int NATIVE_BAD_DOCUMENT = -1;
    private static final int NATIVE_START_DOCUMENT = 0;
    private static final int NATIVE_END_DOCUMENT = 1;
    private static final int NATIVE_START_NAMESPACE = 0x100;
    private static final int NATIVE_END_NAMESPACE = 0x101;
    private static final int NATIVE_START_TAG = 0x102;
    private static final int NATIVE_END_TAG = 0x103;
    private static final int NATIVE_TEXT = 0x104;

    private static final int A_NS = 0, A_NAME = 1, A_RES = 2, A_STRING = 3, A_TYPE = 4, A_DATA = 5;
    private static final int ATTR_WORDS = 6;
    private static final int HEADER_WORDS = 5;

    final ResEngine.XmlBlock block;
    private final String path;
    private boolean started;
    private int event = START_DOCUMENT;
    private int depth;
    private boolean decNextDepth;
    private int[] current = new int[HEADER_WORDS];

    ResXmlParser(ResEngine.XmlBlock block, String path) {
        this.block = block;
        this.path = path;
    }

    long nativeParser() {
        return block.ptr();
    }

    @Override
    public int next() throws XmlPullParserException, IOException {
        if (!started) {
            started = true;
            return event = START_DOCUMENT;
        }
        if (!block.isOpen()) throw new XmlPullParserException("parser closed");
        int code;
        do {
            code = ResEngine.nXmlNext(block.ptr());
        } while (code == NATIVE_START_NAMESPACE || code == NATIVE_END_NAMESPACE
                || code == NATIVE_START_DOCUMENT);
        if (code == NATIVE_BAD_DOCUMENT) {
            throw new XmlPullParserException("Corrupt XML binary file: " + path);
        }
        if (decNextDepth) {
            depth--;
            decNextDepth = false;
        }
        switch (code) {
            case NATIVE_START_TAG: event = START_TAG; depth++; break;
            case NATIVE_END_TAG: event = END_TAG; decNextDepth = true; break;
            case NATIVE_TEXT: event = TEXT; break;
            case NATIVE_END_DOCUMENT: event = END_DOCUMENT; break;
            default: throw new XmlPullParserException("Corrupt XML binary file: " + path);
        }
        int[] words = event == END_DOCUMENT ? null : ResEngine.nXmlCurrent(block.ptr());
        current = words != null && words.length >= HEADER_WORDS ? words : new int[HEADER_WORDS];
        return event;
    }

    @Override
    public int nextToken() throws XmlPullParserException, IOException {
        return next();
    }

    @Override
    public int getEventType() {
        return event;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public String getName() {
        return event == START_TAG || event == END_TAG ? block.string(current[1]) : null;
    }

    @Override
    public String getNamespace() {
        String ns = event == START_TAG || event == END_TAG ? block.string(current[2]) : null;
        return ns == null ? "" : ns;
    }

    @Override
    public String getPrefix() {
        return null;
    }

    @Override
    public String getText() {
        return event == TEXT ? block.string(current[3]) : null;
    }

    @Override
    public char[] getTextCharacters(int[] holderForStartAndLength) {
        String text = getText();
        if (text == null) return null;
        holderForStartAndLength[0] = 0;
        holderForStartAndLength[1] = text.length();
        return text.toCharArray();
    }

    @Override
    public boolean isWhitespace() {
        String text = getText();
        return text != null && text.trim().isEmpty();
    }

    @Override
    public boolean isEmptyElementTag() {
        return false;
    }

    @Override
    public int getLineNumber() {
        return current[0];
    }

    @Override
    public int getColumnNumber() {
        return -1;
    }

    @Override
    public String getPositionDescription() {
        return "Binary XML file " + path + " line #" + getLineNumber();
    }

    @Override
    public int getAttributeCount() {
        return event == START_TAG ? current[4] : -1;
    }

    private int word(int index, int field) {
        if (event != START_TAG || index < 0 || index >= current[4]) {
            throw new IndexOutOfBoundsException("attribute " + index);
        }
        return current[HEADER_WORDS + index * ATTR_WORDS + field];
    }

    @Override
    public String getAttributeNamespace(int index) {
        String ns = block.string(word(index, A_NS));
        return ns == null ? "" : ns;
    }

    @Override
    public String getAttributeName(int index) {
        return block.string(word(index, A_NAME));
    }

    @Override
    public String getAttributePrefix(int index) {
        return null;
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
    public int getAttributeNameResource(int index) {
        return word(index, A_RES);
    }

    @Override
    public int[] getAttributeRawValue(int index) {
        return new int[] {word(index, A_TYPE), word(index, A_DATA)};
    }

    @Override
    public String getAttributeValue(int index) {
        int str = word(index, A_STRING);
        if (str >= 0) return block.string(str);
        int type = word(index, A_TYPE);
        int data = word(index, A_DATA);
        if (type == TypedValue.TYPE_STRING) return block.string(data);
        return TypedValue.coerceToString(type, data);
    }

    /** AOSP ResXMLParser::indexOfAttribute: a null namespace matches only un-namespaced attributes. */
    private int indexOf(String namespace, String name) {
        if (event != START_TAG || name == null) return -1;
        for (int i = 0; i < current[4]; i++) {
            if (!name.equals(block.string(word(i, A_NAME)))) continue;
            String ns = block.string(word(i, A_NS));
            if (namespace == null ? ns == null : namespace.equals(ns)) return i;
        }
        return -1;
    }

    @Override
    public String getAttributeValue(String namespace, String name) {
        int i = indexOf(namespace, name);
        return i < 0 ? null : getAttributeValue(i);
    }

    @Override
    public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) {
        int i = indexOf(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeListValue(i, options, defaultValue);
    }

    @Override
    public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
        int i = indexOf(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeBooleanValue(i, defaultValue);
    }

    @Override
    public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
        int i = indexOf(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeResourceValue(i, defaultValue);
    }

    @Override
    public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
        int i = indexOf(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeIntValue(i, defaultValue);
    }

    @Override
    public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) {
        int i = indexOf(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeUnsignedIntValue(i, defaultValue);
    }

    @Override
    public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
        int i = indexOf(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeFloatValue(i, defaultValue);
    }

    @Override
    public int getAttributeListValue(int index, String[] options, int defaultValue) {
        int type = word(index, A_TYPE);
        int data = word(index, A_DATA);
        if (type == TypedValue.TYPE_STRING) {
            String value = block.string(data);
            if (value == null || options == null) return defaultValue;
            for (int i = 0; i < options.length; i++) if (options[i].equals(value)) return i;
            return defaultValue;
        }
        return data;
    }

    private static boolean isInt(int type) {
        return type >= TypedValue.TYPE_FIRST_INT && type <= TypedValue.TYPE_LAST_INT;
    }

    @Override
    public boolean getAttributeBooleanValue(int index, boolean defaultValue) {
        return isInt(word(index, A_TYPE)) ? word(index, A_DATA) != 0 : defaultValue;
    }

    @Override
    public int getAttributeResourceValue(int index, int defaultValue) {
        return word(index, A_TYPE) == TypedValue.TYPE_REFERENCE ? word(index, A_DATA) : defaultValue;
    }

    @Override
    public int getAttributeIntValue(int index, int defaultValue) {
        return isInt(word(index, A_TYPE)) ? word(index, A_DATA) : defaultValue;
    }

    @Override
    public int getAttributeUnsignedIntValue(int index, int defaultValue) {
        return getAttributeIntValue(index, defaultValue);
    }

    @Override
    public float getAttributeFloatValue(int index, float defaultValue) {
        if (word(index, A_TYPE) == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(word(index, A_DATA));
        throw new RuntimeException("not a float!");
    }

    @Override
    public int getStyleAttribute() {
        int i = indexOf(null, "style");
        if (i < 0) return 0;
        int type = word(i, A_TYPE);
        return type == TypedValue.TYPE_REFERENCE || type == TypedValue.TYPE_ATTRIBUTE ? word(i, A_DATA) : 0;
    }

    @Override
    public void recycle() {}

    @Override
    public void close() {
        block.close();
    }

    @Override
    public void setFeature(String name, boolean state) throws XmlPullParserException {
        if (FEATURE_PROCESS_NAMESPACES.equals(name) && state) return;
        if (FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name) && state) return;
        throw new XmlPullParserException("Unsupported feature: " + name);
    }

    @Override
    public boolean getFeature(String name) {
        return FEATURE_PROCESS_NAMESPACES.equals(name) || FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name);
    }

    @Override
    public void setProperty(String name, Object value) throws XmlPullParserException {
        throw new XmlPullParserException("setProperty() not supported");
    }

    @Override
    public Object getProperty(String name) {
        return null;
    }

    @Override
    public void setInput(java.io.InputStream in, String inputEncoding) throws XmlPullParserException {
        throw new XmlPullParserException("setInput() not supported");
    }

    @Override
    public void setInput(java.io.Reader in) throws XmlPullParserException {
        throw new XmlPullParserException("setInput() not supported");
    }

    @Override
    public String getInputEncoding() {
        return null;
    }

    @Override
    public void defineEntityReplacementText(String entityName, String replacementText)
            throws XmlPullParserException {
        throw new XmlPullParserException("defineEntityReplacementText() not supported");
    }

    @Override
    public int getNamespaceCount(int depth) throws XmlPullParserException {
        throw new XmlPullParserException("getNamespaceCount() not supported");
    }

    @Override
    public String getNamespacePrefix(int pos) throws XmlPullParserException {
        throw new XmlPullParserException("getNamespacePrefix() not supported");
    }

    @Override
    public String getNamespaceUri(int pos) throws XmlPullParserException {
        throw new XmlPullParserException("getNamespaceUri() not supported");
    }

    @Override
    public String getNamespace(String prefix) {
        throw new RuntimeException("getNamespace() not supported");
    }

    @Override
    public void require(int type, String namespace, String name) throws XmlPullParserException {
        if (type != event
                || (namespace != null && !namespace.equals(getNamespace()))
                || (name != null && !name.equals(getName()))) {
            throw new XmlPullParserException("expected " + TYPES[type] + " " + getPositionDescription());
        }
    }

    @Override
    public String nextText() throws XmlPullParserException, IOException {
        if (event != START_TAG) throw new XmlPullParserException(getPositionDescription() + ": parser must be on START_TAG to read next text");
        int t = next();
        if (t == TEXT) {
            String result = getText();
            if (next() != END_TAG) throw new XmlPullParserException(getPositionDescription() + ": event TEXT it must be immediately followed by END_TAG");
            return result;
        }
        if (t == END_TAG) return "";
        throw new XmlPullParserException(getPositionDescription() + ": parser must be on START_TAG or TEXT to read text");
    }

    @Override
    public int nextTag() throws XmlPullParserException, IOException {
        int t = next();
        if (t == TEXT && isWhitespace()) t = next();
        if (t != START_TAG && t != END_TAG) throw new XmlPullParserException(getPositionDescription() + ": expected start or end tag");
        return t;
    }
}
