// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;

/** Paragraph leading-margin span with indent getters. */
public interface LeadingMarginSpan {
    int getLeadingMargin(boolean first);

    void drawLeadingMargin(android.graphics.Canvas c, android.graphics.Paint p, int x, int dir,
            int top, int baseline, int bottom, CharSequence text, int start, int end,
            boolean first, android.text.Layout layout);

    /** Leading-margin span with separate first/rest indents. */
    class Standard implements LeadingMarginSpan, Parcelable {
        private final int first;
        private final int rest;

        public Standard(int first, int rest) {
            this.first = first;
            this.rest = rest;
        }

        public Standard(int every) {
            this(every, every);
        }

        public Standard(Parcel src) {
            this.first = src.readInt();
            this.rest = src.readInt();
        }

        public static final Parcelable.Creator<Standard> CREATOR =
                new Parcelable.Creator<Standard>() {
                    @Override public Standard createFromParcel(Parcel source) {
                        return new Standard(source);
                    }
                    @Override public Standard[] newArray(int size) {
                        return new Standard[size];
                    }
                };

        public int getFirst() {
            return first;
        }

        public int getRest() {
            return rest;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return first ? this.first : rest;
        }

        @Override
        public void drawLeadingMargin(android.graphics.Canvas c, android.graphics.Paint p, int x,
                int dir, int top, int baseline, int bottom, CharSequence text, int start, int end,
                boolean first, android.text.Layout layout) {
        }

        @Override public int describeContents() { return 0; }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(first);
            dest.writeInt(rest);
        }
    }
}
