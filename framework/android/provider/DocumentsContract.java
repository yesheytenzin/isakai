// SPDX-License-Identifier: GPL-3.0-or-later
package android.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import java.util.List;

/**
 * DocumentsContract subset: the URI shapes documents providers expose plus
 * {@link #createDocument}, routed in-process through
 * {@link ContentResolver#call} — there is no Binder, so a call reaches the
 * provider object registered in this process or fails closed.
 */
public final class DocumentsContract {
    /** Intent action a documents provider advertises. */
    public static final String PROVIDER_INTERFACE = "android.content.action.DOCUMENTS_PROVIDER";
    /** Provider call method: create a document (AOSP value). */
    public static final String METHOD_CREATE_DOCUMENT = "android:createDocument";
    /** Bundle key: the document Uri (AOSP value). */
    public static final String EXTRA_URI = "android.provider.extra.URI";
    static final String EXTRA_DISPLAY_NAME = "android.provider.extra.DISPLAY_NAME";
    static final String EXTRA_MIME_TYPE = "android.provider.extra.MIME_TYPE";

    private static final String PATH_DOCUMENT = "document";
    private static final String PATH_TREE = "tree";
    private static final String PATH_CHILD = "child";

    private DocumentsContract() {}

    public static Uri buildDocumentUri(String authority, String documentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority)
                .appendPath(PATH_DOCUMENT).appendPath(documentId).build();
    }

    public static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(treeUri.getAuthority())
                .appendPath(PATH_TREE).appendPath(getTreeDocumentId(treeUri))
                .appendPath(PATH_DOCUMENT).appendPath(documentId).build();
    }

    public static Uri buildChildDocumentsUri(String authority, String parentDocumentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority)
                .appendPath(PATH_DOCUMENT).appendPath(parentDocumentId).appendPath(PATH_CHILD).build();
    }

    public static Uri buildChildDocumentsUriUsingTree(Uri treeUri, String parentDocumentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(treeUri.getAuthority())
                .appendPath(PATH_TREE).appendPath(getTreeDocumentId(treeUri))
                .appendPath(PATH_DOCUMENT).appendPath(parentDocumentId).appendPath(PATH_CHILD).build();
    }

    public static String getDocumentId(Uri documentUri) {
        List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && PATH_DOCUMENT.equals(paths.get(0))) {
            return paths.get(1);
        }
        if (paths.size() >= 4 && PATH_TREE.equals(paths.get(0)) && PATH_DOCUMENT.equals(paths.get(2))) {
            return paths.get(3);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    public static String getTreeDocumentId(Uri documentUri) {
        List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0))) {
            return paths.get(1);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    /**
     * AOSP: create a document under {@code parentDocumentUri} by calling the
     * hosting provider. The provider returns the new document Uri in its
     * result bundle; unknown authorities fail closed through
     * {@link ContentResolver#call}.
     */
    public static Uri createDocument(ContentResolver resolver, Uri parentDocumentUri,
            String mimeType, String displayName) {
        Bundle in = new Bundle();
        in.putString(EXTRA_URI, parentDocumentUri == null ? null : parentDocumentUri.toString());
        in.putString(EXTRA_DISPLAY_NAME, displayName);
        in.putString(EXTRA_MIME_TYPE, mimeType);
        Bundle out = resolver.call(parentDocumentUri, METHOD_CREATE_DOCUMENT, null, in);
        if (out == null) return null;
        Object created = out.get(EXTRA_URI);
        return created instanceof Uri ? (Uri) created : null;
    }
}
