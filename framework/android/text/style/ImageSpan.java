// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Image placeholder span: drawable/bitmap/uri/context variants with getters. */
public class ImageSpan extends ReplacementSpan implements Parcelable {
    public static final int ALIGN_BOTTOM = 0;
    public static final int ALIGN_BASELINE = 1;

    private final Drawable drawable;
    private final String source;
    private final Uri uri;
    private final int resourceId;
    private final int alignment;

    public ImageSpan(Drawable drawable) {
        this(drawable, ALIGN_BOTTOM);
    }

    public ImageSpan(Drawable drawable, int verticalAlignment) {
        this.drawable = drawable;
        this.source = null;
        this.uri = null;
        this.resourceId = 0;
        this.alignment = verticalAlignment;
    }

    public ImageSpan(Drawable drawable, String source) {
        this(drawable, source, ALIGN_BOTTOM);
    }

    public ImageSpan(Drawable drawable, String source, int verticalAlignment) {
        this.drawable = drawable;
        this.source = source;
        this.uri = null;
        this.resourceId = 0;
        this.alignment = verticalAlignment;
    }

    public ImageSpan(Context context, Bitmap bitmap) {
        this(context, bitmap, ALIGN_BOTTOM);
    }

    public ImageSpan(Context context, Bitmap bitmap, int verticalAlignment) {
        this(new BitmapDrawable(context == null ? null : context.getResources(), bitmap),
                (String) null, verticalAlignment);
    }

    public ImageSpan(Context context, Uri uri) {
        this(context, uri, ALIGN_BOTTOM);
    }

    public ImageSpan(Context context, Uri uri, int verticalAlignment) {
        this.drawable = null;
        this.source = uri == null ? null : uri.toString();
        this.uri = uri;
        this.resourceId = 0;
        this.alignment = verticalAlignment;
    }

    public ImageSpan(Context context, int resourceId) {
        this(context, resourceId, ALIGN_BOTTOM);
    }

    public ImageSpan(Context context, int resourceId, int verticalAlignment) {
        this.drawable = context == null ? null : context.getDrawable(resourceId);
        this.source = null;
        this.uri = null;
        this.resourceId = resourceId;
        this.alignment = verticalAlignment;
    }

    public ImageSpan(Parcel src) {
        this.drawable = null;
        this.source = src.readString();
        this.uri = null;
        this.resourceId = 0;
        this.alignment = src.readInt();
    }

    public static final Parcelable.Creator<ImageSpan> CREATOR =
            new Parcelable.Creator<ImageSpan>() {
                @Override public ImageSpan createFromParcel(Parcel source) {
                    return new ImageSpan(source);
                }
                @Override public ImageSpan[] newArray(int size) {
                    return new ImageSpan[size];
                }
            };

    public Drawable getDrawable() {
        return drawable;
    }

    public String getSource() {
        return source;
    }

    public int getVerticalAlignment() {
        return alignment;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
            Paint.FontMetricsInt fm) {
        Drawable d = getDrawable();
        if (d == null || paint == null) return 0;
        if (fm != null) {
            Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
            fm.top = pfm.top;
            fm.ascent = pfm.ascent;
            fm.descent = pfm.descent;
            fm.bottom = pfm.bottom;
            fm.leading = pfm.leading;
        }
        return d.getIntrinsicWidth() < 0 ? 0 : d.getIntrinsicWidth();
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
            float x, int top, int y, int bottom, Paint paint) {
    }

    @Override
    public void updateDrawState(TextPaint paint) {
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(source);
        dest.writeInt(alignment);
    }
}
