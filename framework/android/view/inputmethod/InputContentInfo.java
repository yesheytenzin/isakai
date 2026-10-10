// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

import android.content.ClipDescription;
import android.net.Uri;

/** IME content payload: uris plus description. */
public class InputContentInfo {
    private final Uri contentUri;
    private final ClipDescription description;
    private final Uri linkUri;

    public InputContentInfo(Uri contentUri, ClipDescription description) {
        this(contentUri, description, null);
    }

    public InputContentInfo(Uri contentUri, ClipDescription description, Uri linkUri) {
        this.contentUri = contentUri;
        this.description = description;
        this.linkUri = linkUri;
    }

    public Uri getContentUri() {
        return contentUri;
    }

    public ClipDescription getDescription() {
        return description;
    }

    public Uri getLinkUri() {
        return linkUri;
    }

    public void requestPermission() {}

    public void releasePermission() {}
}
