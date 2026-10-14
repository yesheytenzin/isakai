// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** Task and memory state queries; safe defaults. */
public class ActivityManager {
    public static final class TaskDescription {
        private final String label;
        private final android.graphics.Bitmap icon;
        private final int primaryColor;
        public TaskDescription(String label,android.graphics.Bitmap icon,int color) {
            if(color!=0 && (color>>>24)!=255)throw new IllegalArgumentException("primary color must be opaque");
            this.label=label;this.icon=icon;primaryColor=color;
        }
        public String getLabel(){return label;}
        public android.graphics.Bitmap getIcon(){return icon;}
        public int getPrimaryColor(){return primaryColor;}
    }
    public static class RunningAppProcessInfo {
        public static final int IMPORTANCE_FOREGROUND = 100;
        public static final int IMPORTANCE_VISIBLE = 200;
        public static final int IMPORTANCE_SERVICE = 300;
        public static final int IMPORTANCE_CACHED = 400;
        public static final int REASON_UNKNOWN = 0;
        public String processName;
        public int pid;
        public int uid;
        public int importance;
        public int importanceReasonCode;
        public String[] pkgList;
    }
    public static class RunningServiceInfo {
        public String service;
        public int pid;
        public int uid;
        public String process;
        public boolean foreground;
        public long activeSince;
    }
    public static class RunningTaskInfo {
        public int id;
        public int numActivities;
        public CharSequence description;
    }
    public static class MemoryInfo {
        public long availMem;
        public long totalMem;
        public long threshold;
        public boolean lowMemory;
    }
    public java.util.List<RunningAppProcessInfo> getRunningAppProcesses() { return java.util.Collections.emptyList(); }
    public java.util.List<RunningServiceInfo> getRunningServices(int maxNum) { return java.util.Collections.emptyList(); }
    public java.util.List<RunningTaskInfo> getRunningTasks(int maxNum) { return java.util.Collections.emptyList(); }
    public void getMemoryInfo(MemoryInfo outInfo) { if (outInfo != null) { outInfo.availMem = 0; outInfo.totalMem = 0; outInfo.threshold = 0; outInfo.lowMemory = false; } }
    public int getMemoryClass() { return 0; }
    public int getLargeMemoryClass() { return 0; }
    public boolean isLowRamDevice() { return false; }
    public void killBackgroundProcesses(String packageName) { }
    public static boolean isRunningInTestHarness() { return false; }
    public static boolean isUserAMonkey() { return false; }
}
