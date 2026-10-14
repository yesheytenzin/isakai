// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

/** XML factory: pull parser / serializer backed by the JDK StAX engine. */
public class Xml {
    private Xml() {}

    public static XmlPullParser newPullParser() {
        return new StaxPullParser();
    }

    public static AttributeSet asAttributeSet(XmlPullParser parser) {
        if(parser==null)throw new NullPointerException("parser");
        return parser instanceof AttributeSet?(AttributeSet)parser:new PullAttributes(parser);
    }
    private static final class PullAttributes implements AttributeSet {
        private final XmlPullParser parser;
        PullAttributes(XmlPullParser parser) { this.parser=parser; }
        public int getAttributeCount(){return parser.getAttributeCount();}
        public String getAttributeName(int index){return parser.getAttributeName(index);}
        public String getAttributeValue(int index){return parser.getAttributeValue(index);}
        public String getAttributeValue(String namespace,String name){return parser.getAttributeValue(namespace,name);}
        public String getPositionDescription(){return parser.getPositionDescription();}
        public int getAttributeNameResource(int index){return 0;}
        public int getAttributeListValue(String namespace,String name,String[] options,int fallback){
            String value=getAttributeValue(namespace,name);
            for(int i=0;i<options.length;i++)if(options[i].equals(value))return i;
            return fallback;
        }
        public boolean getAttributeBooleanValue(String namespace,String name,boolean fallback){
            String value=getAttributeValue(namespace,name);
            if("true".equals(value))return true;if("false".equals(value))return false;
            return value==null?fallback:getAttributeIntValue(namespace,name,fallback?1:0)!=0;
        }
        public int getAttributeResourceValue(String namespace,String name,int fallback){
            String value=getAttributeValue(namespace,name);
            if(value==null || !value.startsWith("@"))return fallback;
            try{return Integer.decode(value.substring(1));}catch(NumberFormatException e){return fallback;}
        }
        public int getAttributeIntValue(String namespace,String name,int fallback){
            String value=getAttributeValue(namespace,name);
            if(value==null)return fallback;
            try{return Integer.decode(value);}catch(NumberFormatException e){return fallback;}
        }
        public int getAttributeUnsignedIntValue(String namespace,String name,int fallback){
            int value=getAttributeIntValue(namespace,name,fallback);return value<0?fallback:value;
        }
        public float getAttributeFloatValue(String namespace,String name,float fallback){
            String value=getAttributeValue(namespace,name);
            if(value==null)return fallback;
            try{return Float.parseFloat(value);}catch(NumberFormatException e){return fallback;}
        }
        public int getAttributeListValue(int index,String[] options,int fallback){
            return getAttributeListValue(parser.getAttributeNamespace(index),parser.getAttributeName(index),options,fallback);
        }
        public boolean getAttributeBooleanValue(int index,boolean fallback){
            return getAttributeBooleanValue(parser.getAttributeNamespace(index),parser.getAttributeName(index),fallback);
        }
        public int getAttributeResourceValue(int index,int fallback){
            return getAttributeResourceValue(parser.getAttributeNamespace(index),parser.getAttributeName(index),fallback);
        }
        public int getAttributeIntValue(int index,int fallback){
            return getAttributeIntValue(parser.getAttributeNamespace(index),parser.getAttributeName(index),fallback);
        }
        public int getAttributeUnsignedIntValue(int index,int fallback){
            return getAttributeUnsignedIntValue(parser.getAttributeNamespace(index),parser.getAttributeName(index),fallback);
        }
        public float getAttributeFloatValue(int index,float fallback){
            return getAttributeFloatValue(parser.getAttributeNamespace(index),parser.getAttributeName(index),fallback);
        }
        public int getStyleAttribute(){return getAttributeResourceValue(null,"style",0);}
        public void recycle(){}
    }

    public static XmlSerializer newSerializer() {
        return new StaxSerializer();
    }

    /** StAX-backed pull parser. Namespace-aware; validation unsupported. */
    private static class StaxPullParser implements XmlPullParser {
        private XMLEventReader reader;
        private XMLEvent current;
        private int event = START_DOCUMENT;
        private String inputEncoding;
        private boolean processNamespaces = true;

        private void ensureReader() throws XmlPullParserException {
            if (reader == null) throw new XmlPullParserException("input not set");
        }

        @Override
        public void setFeature(String name, boolean state) throws XmlPullParserException {
            if (FEATURE_PROCESS_NAMESPACES.equals(name)) {
                processNamespaces = state;
                return;
            }
            if (!state) return;
            throw new XmlPullParserException("unsupported feature: " + name);
        }

        @Override
        public boolean getFeature(String name) {
            if (FEATURE_PROCESS_NAMESPACES.equals(name)) return processNamespaces;
            return false;
        }

        @Override
        public void setProperty(String name, Object value) throws XmlPullParserException {
            throw new XmlPullParserException("unsupported property: " + name);
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public void setInput(InputStream in, String inputEncoding) throws XmlPullParserException {
            try {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, processNamespaces);
                this.reader = factory.createXMLEventReader(in, inputEncoding);
                this.inputEncoding = inputEncoding;
                this.current = null;
                this.event = START_DOCUMENT;
            } catch (XMLStreamException e) {
                throw new XmlPullParserException("input failed", this, e);
            }
        }

        @Override
        public void setInput(Reader in) throws XmlPullParserException {
            try {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, processNamespaces);
                this.reader = factory.createXMLEventReader(in);
                this.current = null;
                this.event = START_DOCUMENT;
            } catch (XMLStreamException e) {
                throw new XmlPullParserException("input failed", this, e);
            }
        }

        @Override
        public String getInputEncoding() {
            return inputEncoding;
        }

        @Override
        public void defineEntityReplacementText(String entityName, String replacementText)
                throws XmlPullParserException {}

        @Override
        public int getNamespaceCount(int depth) throws XmlPullParserException {
            return 0;
        }

        @Override
        public String getNamespacePrefix(int pos) throws XmlPullParserException {
            return null;
        }

        @Override
        public String getNamespaceUri(int pos) throws XmlPullParserException {
            return null;
        }

        @Override
        public String getNamespace(String prefix) {
            return NO_NAMESPACE;
        }

        @Override
        public int getDepth() {
            return 0;
        }

        @Override
        public String getPositionDescription() {
            if (current == null || current.getLocation() == null) return "@?-1:-1";
            return "@" + current.getLocation().getLineNumber()
                    + ":" + current.getLocation().getColumnNumber();
        }

        @Override
        public int getLineNumber() {
            if (current == null || current.getLocation() == null) return -1;
            return current.getLocation().getLineNumber();
        }

        @Override
        public int getColumnNumber() {
            if (current == null || current.getLocation() == null) return -1;
            return current.getLocation().getColumnNumber();
        }

        @Override
        public boolean isWhitespace() throws XmlPullParserException {
            return event == IGNORABLE_WHITESPACE
                    || (event == TEXT && current != null && current.isCharacters()
                            && current.asCharacters().isWhiteSpace());
        }

        @Override
        public String getText() {
            if (current == null) return null;
            if (current.isCharacters()) return current.asCharacters().getData();
            if (current.getEventType() == javax.xml.stream.XMLStreamConstants.COMMENT) {
                return ((javax.xml.stream.events.Comment) current).getText();
            }
            if (current.getEventType()
                    == javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION) {
                return ((javax.xml.stream.events.ProcessingInstruction) current).getData();
            }
            if (current.getEventType() == javax.xml.stream.XMLStreamConstants.ENTITY_REFERENCE) {
                return ((javax.xml.stream.events.EntityReference) current).getName();
            }
            return null;
        }

        @Override
        public char[] getTextCharacters(int[] holderForStartAndLength) {
            String text = getText();
            if (text == null) return null;
            char[] out = text.toCharArray();
            holderForStartAndLength[0] = 0;
            holderForStartAndLength[1] = out.length;
            return out;
        }

        private javax.xml.namespace.QName elementName() {
            if (current == null) return null;
            if (current.isStartElement()) return current.asStartElement().getName();
            if (current.isEndElement()) return current.asEndElement().getName();
            return null;
        }

        @Override
        public String getNamespace() {
            javax.xml.namespace.QName name = elementName();
            if (name == null) return null;
            String uri = name.getNamespaceURI();
            return uri == null ? NO_NAMESPACE : uri;
        }

        @Override
        public String getName() {
            javax.xml.namespace.QName name = elementName();
            return name == null ? null : name.getLocalPart();
        }

        @Override
        public String getPrefix() {
            javax.xml.namespace.QName name = elementName();
            return name == null ? null : name.getPrefix();
        }

        @Override
        public boolean isEmptyElementTag() throws XmlPullParserException {
            return false;
        }

        private StartElement start() {
            if (current != null && current.isStartElement()) return current.asStartElement();
            return null;
        }

        @Override
        public int getAttributeCount() {
            StartElement s = start();
            if (s == null) return 0;
            int n = 0;
            java.util.Iterator<?> it = s.getAttributes();
            while (it.hasNext()) {
                it.next();
                n++;
            }
            return n;
        }

        private Attribute attributeAt(int index) {
            StartElement s = start();
            if (s == null) return null;
            java.util.Iterator<?> it = s.getAttributes();
            for (int i = 0; i <= index; i++) {
                if (!it.hasNext()) return null;
                Object o = it.next();
                if (i == index) return (Attribute) o;
            }
            return null;
        }

        @Override
        public String getAttributeNamespace(int index) {
            Attribute a = attributeAt(index);
            if (a == null) return NO_NAMESPACE;
            String uri = a.getName().getNamespaceURI();
            return uri == null ? NO_NAMESPACE : uri;
        }

        @Override
        public String getAttributeName(int index) {
            Attribute a = attributeAt(index);
            return a == null ? null : a.getName().getLocalPart();
        }

        @Override
        public String getAttributePrefix(int index) {
            Attribute a = attributeAt(index);
            return a == null ? null : a.getName().getPrefix();
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
            Attribute a = attributeAt(index);
            return a == null ? null : a.getValue();
        }

        @Override
        public String getAttributeValue(String namespace, String name) {
            StartElement s = start();
            if (s == null) return null;
            java.util.Iterator<?> it = s.getAttributes();
            while (it.hasNext()) {
                Attribute a = (Attribute) it.next();
                String uri = a.getName().getNamespaceURI();
                if (uri == null) uri = NO_NAMESPACE;
                String want = namespace == null ? NO_NAMESPACE : namespace;
                if (uri.equals(want) && a.getName().getLocalPart().equals(name)) {
                    return a.getValue();
                }
            }
            return null;
        }

        @Override
        public int getEventType() throws XmlPullParserException {
            ensureReader();
            return event;
        }

        @Override
        public int next() throws XmlPullParserException, IOException {
            ensureReader();
            try {
                while (reader.hasNext()) {
                    XMLEvent e = reader.nextEvent();
                    current = e;
                    switch (e.getEventType()) {
                        case javax.xml.stream.XMLStreamConstants.START_DOCUMENT:
                            continue;
                        case javax.xml.stream.XMLStreamConstants.END_DOCUMENT:
                            event = END_DOCUMENT;
                            return event;
                        case javax.xml.stream.XMLStreamConstants.START_ELEMENT:
                            event = START_TAG;
                            return event;
                        case javax.xml.stream.XMLStreamConstants.END_ELEMENT:
                            event = END_TAG;
                            return event;
                        case javax.xml.stream.XMLStreamConstants.CHARACTERS:
                        case javax.xml.stream.XMLStreamConstants.CDATA:
                            Characters ch = e.asCharacters();
                            if (ch.isWhiteSpace()) {
                                event = IGNORABLE_WHITESPACE;
                            } else {
                                event = e.getEventType()
                                                == javax.xml.stream.XMLStreamConstants.CDATA
                                        ? CDSECT
                                        : TEXT;
                            }
                            return event;
                        case javax.xml.stream.XMLStreamConstants.SPACE:
                            event = IGNORABLE_WHITESPACE;
                            return event;
                        case javax.xml.stream.XMLStreamConstants.ENTITY_REFERENCE:
                            event = ENTITY_REF;
                            return event;
                        case javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION:
                            event = PROCESSING_INSTRUCTION;
                            return event;
                        case javax.xml.stream.XMLStreamConstants.COMMENT:
                            event = COMMENT;
                            return event;
                        case javax.xml.stream.XMLStreamConstants.DTD:
                            event = DOCDECL;
                            return event;
                        default:
                            break;
                    }
                }
                event = END_DOCUMENT;
                return event;
            } catch (XMLStreamException e) {
                throw new XmlPullParserException("next failed", this, e);
            }
        }

        @Override
        public int nextToken() throws XmlPullParserException, IOException {
            return next();
        }

        @Override
        public void require(int type, String namespace, String name)
                throws XmlPullParserException, IOException {
            if (type != event) {
                throw new XmlPullParserException(
                        "expected " + TYPES[type] + " got " + TYPES[event], this, null);
            }
            if (namespace != null && !namespace.equals(getNamespace())) {
                throw new XmlPullParserException("namespace mismatch", this, null);
            }
            if (name != null && !name.equals(getName())) {
                throw new XmlPullParserException("name mismatch", this, null);
            }
        }

        @Override
        public String nextText() throws XmlPullParserException, IOException {
            if (event != START_TAG) {
                throw new XmlPullParserException("nextText outside START_TAG", this, null);
            }
            int t = next();
            if (t == TEXT || t == CDSECT) {
                String text = getText();
                if (next() != END_TAG) {
                    throw new XmlPullParserException("mixed content", this, null);
                }
                return text;
            }
            if (t == END_TAG) return "";
            throw new XmlPullParserException("unexpected event in nextText", this, null);
        }

        @Override
        public int nextTag() throws XmlPullParserException, IOException {
            int t = next();
            while (t == IGNORABLE_WHITESPACE || t == COMMENT || t == PROCESSING_INSTRUCTION
                    || (t == TEXT || t == CDSECT) && isWhitespace()) t = next();
            if (t != START_TAG && t != END_TAG) {
                throw new XmlPullParserException("expected tag", this, null);
            }
            return t;
        }
    }

    /** StAX-backed serializer. */
    private static class StaxSerializer implements XmlSerializer {
        private XMLStreamWriter writer;

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
        public void setOutput(java.io.OutputStream os, String encoding)
                throws IOException, IllegalArgumentException, IllegalStateException {
            try {
                writer = XMLOutputFactory.newInstance()
                        .createXMLStreamWriter(os, encoding == null ? "UTF-8" : encoding);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void setOutput(Writer writer)
                throws IOException, IllegalArgumentException, IllegalStateException {
            try {
                this.writer =
                        XMLOutputFactory.newInstance().createXMLStreamWriter(writer);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        private void ensureWriter() {
            if (writer == null) throw new IllegalStateException("output not set");
        }

        @Override
        public void startDocument(String encoding, Boolean standalone)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                if (encoding == null) {
                    writer.writeStartDocument();
                } else {
                    // StAX has no standalone overload; standalone is dropped.
                    writer.writeStartDocument(encoding, "1.0");
                }
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void endDocument()
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeEndDocument();
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void setPrefix(String prefix, String namespace)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.setPrefix(prefix, namespace);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public String getPrefix(String namespace, boolean generatePrefix)
                throws IllegalArgumentException {
            if (writer == null) return null;
            try {
                return writer.getPrefix(namespace);
            } catch (XMLStreamException e) {
                return null;
            }
        }

        @Override
        public int getNamespaceCount() throws IllegalStateException {
            ensureWriter();
            try {
                return writer.getNamespaceContext().getPrefixes(javax.xml.XMLConstants.NULL_NS_URI) == null
                        ? 0
                        : 0;
            } catch (Exception e) {
                return 0;
            }
        }

        @Override
        public String getNamespaceUri(String prefix) throws IllegalStateException {
            ensureWriter();
            return writer.getNamespaceContext().getNamespaceURI(prefix);
        }

        @Override
        public String getNamespace() {
            return "";
        }

        @Override
        public XmlSerializer startTag(String namespace, String name)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                if (namespace == null || namespace.isEmpty()) {
                    writer.writeStartElement(name);
                } else {
                    writer.writeStartElement(namespace, name);
                }
                return this;
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public XmlSerializer attribute(String namespace, String name, String value)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                if (namespace == null || namespace.isEmpty()) {
                    writer.writeAttribute(name, value);
                } else {
                    writer.writeAttribute(namespace, name, value);
                }
                return this;
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public XmlSerializer text(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeCharacters(text == null ? "" : text);
                return this;
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public XmlSerializer text(char[] buf, int start, int len)
                throws IOException, IllegalArgumentException, IllegalStateException {
            return text(new String(buf, start, len));
        }

        @Override
        public void cdsect(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeCData(text == null ? "" : text);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void entityRef(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeEntityRef(text);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void processingInstruction(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeProcessingInstruction(text);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void comment(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeComment(text == null ? "" : text);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void docdecl(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeDTD(text);
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void ignorableWhitespace(String text)
                throws IOException, IllegalArgumentException, IllegalStateException {
            text(text);
        }

        @Override
        public XmlSerializer endTag(String namespace, String name)
                throws IOException, IllegalArgumentException, IllegalStateException {
            ensureWriter();
            try {
                writer.writeEndElement();
                return this;
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void flush() throws IOException {
            ensureWriter();
            try {
                writer.flush();
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }
    }

}
