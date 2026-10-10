// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** Component factory hook: default instantiation. */
public class AppComponentFactory {
    public AppComponentFactory() {}

    public Application instantiateApplication(ClassLoader cl, String className) throws Exception {
        return (Application) Class.forName(className, true, cl).getDeclaredConstructor().newInstance();
    }

    public Activity instantiateActivity(ClassLoader cl, String className, android.content.Intent intent)
            throws Exception {
        return (Activity) Class.forName(className, true, cl).getDeclaredConstructor().newInstance();
    }
}
