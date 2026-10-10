// SPDX-License-Identifier: GPL-3.0-or-later
package org.xmlpull.v1;

/** Serializer contract (Android subset of the XmlPull API). */
public interface XmlSerializer {
    void setFeature(String name, boolean state)
            throws IllegalArgumentException, IllegalStateException;

    boolean getFeature(String name);

    void setProperty(String name, Object value)
            throws IllegalArgumentException, IllegalStateException;

    Object getProperty(String name);

    void setOutput(java.io.OutputStream os, String encoding)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void setOutput(java.io.Writer writer)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void startDocument(String encoding, Boolean standalone) throws java.io.IOException,
            IllegalArgumentException, IllegalStateException;

    void endDocument() throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void setPrefix(String prefix, String namespace)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    String getPrefix(String namespace, boolean generatePrefix)
            throws IllegalArgumentException;

    int getNamespaceCount() throws IllegalStateException;

    String getNamespaceUri(String prefix) throws IllegalStateException;

    String getNamespace();

    XmlSerializer startTag(String namespace, String name)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer attribute(String namespace, String name, String value)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer text(String text)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer text(char[] buf, int start, int len)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void cdsect(String text) throws java.io.IOException, IllegalArgumentException,
            IllegalStateException;

    void entityRef(String text)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void processingInstruction(String text)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void comment(String text)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void docdecl(String text)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void ignorableWhitespace(String text)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer endTag(String namespace, String name)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException;

    void flush() throws java.io.IOException;
}
