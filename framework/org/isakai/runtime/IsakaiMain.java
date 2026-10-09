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
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: IsakaiMain <data-dir> [app-class] [activity-class] [package] [title]");
            System.exit(2);
        }
        String dataDir = args[0];
        Application app = new Application();
        if (args.length > 1 && !args[1].isEmpty() && !args[1].equals("-")) {
            Class<?> appClass = Class.forName(args[1]);
            app = (Application) appClass.getDeclaredConstructor().newInstance();
        }
        app.attach(dataDir);
        System.out.println("isakai: application " + app.getClass().getName() + " onCreate");
        app.onCreate();

        if (args.length > 2 && !args[2].isEmpty() && !args[2].equals("-")) {
            Class<?> actClass = Class.forName(args[2]);
            Activity act = (Activity) actClass.getDeclaredConstructor().newInstance();
            act.attach(app);
            boolean windowed = Boolean.getBoolean("isakai.windowed");
            String pkg = args.length > 3 && args[3] != null && !args[3].isEmpty() ? args[3] : act.getClass().getPackage().getName();
            String title = args.length > 4 && args[4] != null && !args[4].isEmpty() ? args[4] : pkg;
            if (windowed) {
                System.out.println("isakai: activity " + act.getClass().getName() + " runWindowed");
                act.runWindowed(title, pkg);
            } else {
                System.out.println("isakai: activity " + act.getClass().getName() + " runHeadless");
                act.runHeadless();
            }
            System.out.println("isakai: activity " + act.getClass().getName() + " destroyed");
        }
        System.out.println("isakai: run complete");
    }
}
