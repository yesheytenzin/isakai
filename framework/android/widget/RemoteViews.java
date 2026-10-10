// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.app.PendingIntent;
import android.content.Context;

/** Remote view template: records text/visibility/click ops. */
public class RemoteViews {
    private final String packageName;
    private final int layoutId;
    private final java.util.Map<Integer, CharSequence> texts = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> visibility = new java.util.HashMap<>();
    public RemoteViews(String packageName, int layoutId) { this.packageName = packageName; this.layoutId = layoutId; }
    public String getPackage() { return packageName; }
    public int getLayoutId() { return layoutId; }
    public void setTextViewText(int viewId, CharSequence text) { texts.put(viewId, text); }
    public void setViewVisibility(int viewId, int visibility) { this.visibility.put(viewId, visibility); }
    public void setOnClickPendingIntent(int viewId, PendingIntent pendingIntent) {}
    public void reapply(Context context, android.view.ViewGroup parent) {}
    public android.view.View apply(Context context, android.view.ViewGroup parent) { return null; }
}
