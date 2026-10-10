// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import org.xmlpull.v1.XmlPullParser;

/** XML resource parser: closed handle, no content. */
public interface XmlResourceParser extends XmlPullParser, android.util.AttributeSet, AutoCloseable {
    @Override
    void close();

    /** Raw binary-XML {type, data} for the index-th attribute ({0,0} when absent). */
    default int[] getAttributeRawValue(int index) {
        return new int[] {0, 0};
    }
}
