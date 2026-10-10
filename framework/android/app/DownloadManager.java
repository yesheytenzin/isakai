// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

/** Download queue queries; empty shell. */
public class DownloadManager {
    public static final String ACTION_DOWNLOAD_COMPLETE = "android.intent.action.DOWNLOAD_COMPLETE";
    public static final String ACTION_NOTIFICATION_CLICKED = "android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_URI = "uri";
    public static final String COLUMN_TITLE = "title";
    public static final int STATUS_PENDING = 16;
    public static final int STATUS_RUNNING = 8;
    public static final int STATUS_PAUSED = 4;
    public static final int STATUS_SUCCESSFUL = 8 << 4;
    public static final int STATUS_FAILED = 16 << 4;
    public static final int ERROR_UNKNOWN = 1000;
    public static final int VISIBILITY_VISIBLE = 0;
    public static final int VISIBILITY_VISIBLE_NOTIFY_COMPLETED = 1;
    public static final int VISIBILITY_HIDDEN = 2;
    public static class Request {
        public Request(Uri uri) { }
        public Request setTitle(CharSequence title) { return this; }
        public Request setDescription(CharSequence description) { return this; }
        public Request setDestinationInExternalPublicDir(String dirType, String subPath) { return this; }
        public Request setNotificationVisibility(int visibility) { return this; }
        public Request setAllowedNetworkTypes(int flags) { return this; }
    }
    public static class Query {
        public Query setFilterById(long... ids) { return this; }
        public Query setFilterByStatus(int flags) { return this; }
    }
    public long enqueue(Request request) { return -1; }
    public int remove(long... ids) { return 0; }
    public android.database.Cursor query(Query query) { return null; }
    public ParcelFileDescriptor openDownloadedFile(long id) { return null; }
}
