// SPDX-License-Identifier: GPL-3.0-or-later
package org.xmlpull.v1;

/** Pull-parser contract (Android subset of the XmlPull API). */
public interface XmlPullParser {
    int START_DOCUMENT = 0;
    int END_DOCUMENT = 1;
    int START_TAG = 2;
    int END_TAG = 3;
    int TEXT = 4;
    int CDSECT = 5;
    int ENTITY_REF = 6;
    int IGNORABLE_WHITESPACE = 7;
    int PROCESSING_INSTRUCTION = 8;
    int COMMENT = 9;
    int DOCDECL = 10;

    String[] TYPES = {
        "START_DOCUMENT", "END_DOCUMENT", "START_TAG", "END_TAG", "TEXT",
        "CDSECT", "ENTITY_REF", "IGNORABLE_WHITESPACE", "PROCESSING_INSTRUCTION",
        "COMMENT", "DOCDECL"
    };

    String NO_NAMESPACE = "";
    String FEATURE_PROCESS_NAMESPACES =
            "http://xmlpull.org/v1/doc/features.html#process-namespaces";
    String FEATURE_REPORT_NAMESPACE_ATTRIBUTES =
            "http://xmlpull.org/v1/doc/features.html#report-namespace-prefixes";
    String FEATURE_PROCESS_DOCDECL =
            "http://xmlpull.org/v1/doc/features.html#process-docdecl";
    String FEATURE_VALIDATION =
            "http://xmlpull.org/v1/doc/features.html#validation";

    void setFeature(String name, boolean state) throws XmlPullParserException;

    boolean getFeature(String name);

    void setProperty(String name, Object value) throws XmlPullParserException;

    Object getProperty(String name);

    void setInput(java.io.InputStream in, String inputEncoding) throws XmlPullParserException;

    void setInput(java.io.Reader in) throws XmlPullParserException;

    String getInputEncoding();

    void defineEntityReplacementText(String entityName, String replacementText)
            throws XmlPullParserException;

    int getNamespaceCount(int depth) throws XmlPullParserException;

    String getNamespacePrefix(int pos) throws XmlPullParserException;

    String getNamespaceUri(int pos) throws XmlPullParserException;

    String getNamespace(String prefix);

    int getDepth();

    String getPositionDescription();

    int getLineNumber();

    int getColumnNumber();

    boolean isWhitespace() throws XmlPullParserException;

    String getText();

    char[] getTextCharacters(int[] holderForStartAndLength);

    String getNamespace();

    String getName();

    String getPrefix();

    boolean isEmptyElementTag() throws XmlPullParserException;

    int getAttributeCount();

    String getAttributeNamespace(int index);

    String getAttributeName(int index);

    String getAttributePrefix(int index);

    String getAttributeType(int index);

    boolean isAttributeDefault(int index);

    String getAttributeValue(int index);

    String getAttributeValue(String namespace, String name);

    int getEventType() throws XmlPullParserException;

    int next() throws XmlPullParserException, java.io.IOException;

    int nextToken() throws XmlPullParserException, java.io.IOException;

    void require(int type, String namespace, String name)
            throws XmlPullParserException, java.io.IOException;

    String nextText() throws XmlPullParserException, java.io.IOException;

    int nextTag() throws XmlPullParserException, java.io.IOException;
}
