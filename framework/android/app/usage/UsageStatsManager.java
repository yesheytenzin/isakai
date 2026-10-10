// SPDX-License-Identifier: GPL-3.0-or-later
package android.app.usage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Usage stats queries; empty shell. */
public class UsageStatsManager {
    public static final int INTERVAL_DAILY = 0;
    public static final int INTERVAL_WEEKLY = 1;
    public static final int INTERVAL_MONTHLY = 2;
    public static final int INTERVAL_YEARLY = 3;
    public static final int INTERVAL_BEST = 4;
    public List queryUsageStats(int intervalType, long beginTime, long endTime) { return Collections.emptyList(); }
    public Map queryAndAggregateUsageStats(long beginTime, long endTime) { return Collections.emptyMap(); }
    public int getAppStandbyBucket() { return 0; }
    public boolean isAppInactive(String packageName) { return false; }
}
