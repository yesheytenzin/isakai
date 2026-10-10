// SPDX-License-Identifier: GPL-3.0-or-later
package android.media.projection;

import android.content.Intent;

/** Screen capture permission; denied shell. */
public class MediaProjectionManager {
    public Intent createScreenCaptureIntent() { return new Intent(); }
    public Object getMediaProjection(int resultCode, Intent resultData) { return null; }
}
