// SPDX-License-Identifier: GPL-3.0-or-later
package org.xmlpull.v1;

/** Pull-parser failure (parse error or API misuse). */
public class XmlPullParserException extends Exception {
    protected Throwable detail;
    protected int row = -1;
    protected int column = -1;

    public XmlPullParserException() {}

    public XmlPullParserException(String msg) {
        super(msg);
    }

    public XmlPullParserException(String msg, XmlPullParser parser, Throwable chain) {
        super((msg == null ? "" : msg + " ")
                + (parser == null ? "" : "(position:" + parser.getPositionDescription() + ") ")
                + (chain == null ? "" : "caused by: " + chain));
        if (parser != null) {
            this.row = parser.getLineNumber();
            this.column = parser.getColumnNumber();
        }
        this.detail = chain;
    }

    public Throwable getDetail() {
        return detail;
    }

    public int getLineNumber() {
        return row;
    }

    public int getColumnNumber() {
        return column;
    }

    @Override
    public void printStackTrace() {
        if (detail == null) {
            super.printStackTrace();
        } else {
            synchronized (System.err) {
                super.printStackTrace();
                detail.printStackTrace();
            }
        }
    }
}
