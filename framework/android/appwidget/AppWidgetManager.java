// SPDX-License-Identifier: GPL-3.0-or-later
package android.appwidget;

import android.content.ComponentName;
import android.os.Bundle;
import java.util.Collections;
import java.util.List;

/** App widgets; empty shell. */
public class AppWidgetManager {
    public static final String ACTION_APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE";
    public static final String EXTRA_APPWIDGET_IDS = "appWidgetIds";
    public static final String EXTRA_APPWIDGET_ID = "appWidgetId";
    public static final int INVALID_APPWIDGET_ID = 0;

    private static AppWidgetManager instance;

    /** Process-wide singleton, as on device; widget hosting is a no-op. */
    public static AppWidgetManager getInstance(android.content.Context context) {
        if (instance == null) instance = new AppWidgetManager();
        return instance;
    }

    public int[] getAppWidgetIds(ComponentName provider) { return new int[0]; }
    public List getInstalledProviders() { return Collections.emptyList(); }
    public Object getAppWidgetInfo(int appWidgetId) { return null; }
    public void updateAppWidget(int appWidgetId, Object views) { }
    public void updateAppWidget(ComponentName provider, Object views) { }
    public void partiallyUpdateAppWidget(int appWidgetId, Object views) { }
    public Bundle getAppWidgetOptions(int appWidgetId) { return new Bundle(); }
    public void updateAppWidgetOptions(int appWidgetId, Bundle options) { }
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider) { return false; }
}
