// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.runtime;

import android.app.Activity;
import android.app.Application;

/**
 * Entry point for app runs. Args: &lt;data-dir&gt; &lt;app-class&gt;? &lt;activity-class&gt;? &lt;package&gt;? &lt;title&gt;?
 * Instantiates Application (if named), attaches storage, runs the Activity lifecycle.
 * Selects runWindowed when -Disakai.windowed=true, else runHeadless (M1).
 * The title (5th arg) resolves to launcher label, else app label, else package.
 */
public class IsakaiMain {
    private static int parseThemeId(String s) {
        if (s == null || s.isEmpty() || s.equals("-")) return 0;
        try {
            long v = Long.parseLong(s);
            return (int) v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Window title from the launcher: a literal label, or {@code @<id>} for a
     * label resource resolved here for the current configuration; the
     * package when absent or unresolvable.
     */
    private static String windowTitle(String arg, String pkg) {
        if (arg == null || arg.isEmpty()) return pkg;
        if (!arg.startsWith("@")) return arg;
        try {
            return android.content.res.Resources.getSystem().getString(Integer.parseInt(arg.substring(1)));
        } catch (NumberFormatException | android.content.res.Resources.NotFoundException e) {
            return pkg;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: IsakaiMain <data-dir> [app-class] [activity-class] [package] [title]");
            System.exit(2);
        }
        Thread.setDefaultUncaughtExceptionHandler((thread,failure)-> {
            synchronized(System.err) {
                System.err.print("Exception in thread \""+thread.getName()+"\" ");
                failure.printStackTrace(System.err);
            }
            System.exit(1);
        });
        android.os.Looper.prepareMainLooper();
        if (args.length > 7) System.setProperty("isakai.targetSdk", args[7]);
        if (args.length > 8) System.setProperty("isakai.minSdk", args[8]);
        String dataDir = args[0];
        System.setProperty("isakai.dataDir",dataDir);
        System.setProperty("user.home",dataDir);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(dataDir,".cache","fontconfig"));
        Application app = new Application();
        if (args.length > 1 && !args[1].isEmpty() && !args[1].equals("-")) {
            Class<?> appClass = Class.forName(args[1]);
            app = (Application) appClass.getDeclaredConstructor().newInstance();
        }
        app.attach(dataDir);
        if (args.length > 3 && args[3] != null && !args[3].isEmpty()) app.attachPackage(args[3]);
        if (args.length > 5) app.attachTheme(parseThemeId(args[5]));
        System.out.println("isakai: application " + app.getClass().getName() + " onCreate");
        app.onCreate();

        if (args.length > 2 && !args[2].isEmpty() && !args[2].equals("-")) {
            Class<?> actClass = Class.forName(args[2]);
            Activity act = (Activity) actClass.getDeclaredConstructor().newInstance();
            act.attach(app);
            int appTheme = args.length > 5 ? parseThemeId(args[5]) : 0;
            int actTheme = args.length > 6 ? parseThemeId(args[6]) : 0;
            act.attachTheme(appTheme, actTheme);
            if (args.length > 9 && !args[9].isEmpty()) {
                act.setIntent(new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(args[9])));
            }
            boolean windowed = Boolean.getBoolean("isakai.windowed");
            String pkg = args.length > 3 && args[3] != null && !args[3].isEmpty() ? args[3] : act.getClass().getPackage().getName();
            String title = windowTitle(args.length > 4 ? args[4] : null, pkg);
            if (windowed) {
                System.out.println("isakai: activity " + act.getClass().getName() + " runWindowed");
                act.runWindowed(title, pkg);
            } else {
                System.out.println("isakai: activity " + act.getClass().getName() + " runHeadless");
                act.runHeadless();
            }
            System.out.println("isakai: activity " + act.getClass().getName() + " destroyed");
        }
        android.os.Looper.getMainLooper().dispatchPending();
        android.os.Looper.getMainLooper().quitSafely();
        System.out.println("isakai: run complete");
        // One launch owns one JVM. After lifecycle/queue cleanup, app executor
        // threads must not keep a closed desktop window's process alive.
        System.exit(0);
    }
}
