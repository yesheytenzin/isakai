// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * Icon handle: records its source kind plus payload so getters link;
 * loading requires app resources and is unsupported.
 */
public class Icon {
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_BITMAP = 1;
    public static final int TYPE_RESOURCE = 2;
    public static final int TYPE_DATA = 3;
    public static final int TYPE_URI = 4;
    public static final int TYPE_URI_ADAPTIVE_BITMAP = 5;
    public static final int TYPE_ADAPTIVE_BITMAP = 6;

    private final int type;
    private Bitmap bitmap;
    private String resPackage;
    private int resId;
    private byte[] data;
    private int dataOffset;
    private int dataLength;
    private Uri uri;

    private Icon(int type) {
        this.type = type;
    }

    public int getType() { return type; }
    public Bitmap getBitmap() { return bitmap; }
    public String getResPackage() { return resPackage; }
    public int getResId() { return resId; }
    public Uri getUri() { return uri; }

    public static Icon createWithBitmap(Bitmap bits) {
        Icon i = new Icon(TYPE_BITMAP);
        i.bitmap = bits;
        return i;
    }

    public static Icon createWithAdaptiveBitmap(Bitmap bits) {
        Icon i = new Icon(TYPE_ADAPTIVE_BITMAP);
        i.bitmap = bits;
        return i;
    }

    public static Icon createWithResource(String resPackage, int resId) {
        Icon i = new Icon(TYPE_RESOURCE);
        i.resPackage = resPackage;
        i.resId = resId;
        return i;
    }

    public static Icon createWithContentUri(String uri) {
        Icon i = new Icon(TYPE_URI);
        i.uri = uri == null ? null : Uri.parse(uri);
        return i;
    }

    public static Icon createWithContentUri(Uri uri) {
        Icon i = new Icon(TYPE_URI);
        i.uri = uri;
        return i;
    }

    public static Icon createWithData(byte[] data, int offset, int length) {
        Icon i = new Icon(TYPE_DATA);
        i.data = data;
        i.dataOffset = offset;
        i.dataLength = length;
        return i;
    }
}
