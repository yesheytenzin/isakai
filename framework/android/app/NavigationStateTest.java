// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.ComponentName;
import android.content.Intent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reduced state regression; the live NavigationProbe separately drives Wayland. */
public final class NavigationStateTest {
    public static final class Destination extends Activity {
        static String input;
        protected void onCreate(android.os.Bundle saved) {
            input = getIntent().getStringExtra("input");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path code = Files.createTempDirectory("isakai-navigation-state-");
        String previousCode = System.getProperty("isakai.codeDir");
        try {
            String name = Destination.class.getName();
            Files.writeString(code.resolve("AndroidManifest.xml"),
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"test.navigation\">"
                    + "<application android:theme=\"@0x7f120001\">"
                    + "<activity android:name=\"" + name + "\" android:theme=\"@0x7f120002\"/>"
                    + "<activity android:name=\".Default\" android:parentActivityName=\"Parent\"/>"
                    + "</application></manifest>");
            System.setProperty("isakai.codeDir", code.toString());
            android.os.Looper.prepareMainLooper();
            Application app = new Application();
            app.attach(code.toString()); app.attachPackage("test.navigation");
            Activity caller = new Activity(); caller.attach(app);
            caller.attachTheme(0x7f120001, 0x7f120099);
            check(caller.getPackageManager().getApplicationInfo("test.navigation", 0).theme == 0x7f120001,
                    "application manifest theme");
            android.content.pm.ActivityInfo info = caller.getPackageManager().getActivityInfo(
                    new ComponentName("test.navigation", ".Default"), 0);
            check(info.getThemeResource() == 0x7f120001, "application theme fallback");
            check("test.navigation.Parent".equals(info.parentActivityName), "relative parent name");
            try {
                caller.getPackageManager().getActivityInfo(new ComponentName("test.navigation", ".Missing"), 0);
                throw new AssertionError("undeclared activity resolved");
            } catch (android.content.pm.PackageManager.NameNotFoundException expected) {}

            Class<?> type = Class.forName("android.app.Activity$Navigator");
            java.lang.reflect.Constructor<?> ctor = type.getDeclaredConstructor(); ctor.setAccessible(true);
            Object navigator = ctor.newInstance();
            java.lang.reflect.Field stackField = type.getDeclaredField("stack"); stackField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Activity> stack = (java.util.List<Activity>) stackField.get(navigator);
            stack.add(caller);
            java.lang.reflect.Field nav = Activity.class.getDeclaredField("navigator"); nav.setAccessible(true);
            nav.set(caller, navigator);
            Intent sent = new Intent(caller, Destination.class).putExtra("input", "ཀ 中文 😀");
            caller.startActivity(sent);
            sent.putExtra("input", "mutated after launch");
            java.lang.reflect.Method advance = type.getDeclaredMethod("advance"); advance.setAccessible(true);
            advance.invoke(navigator);
            check("ཀ 中文 😀".equals(Destination.input), "launch extras must arrive before onCreate as a snapshot");
            java.lang.reflect.Field theme = Activity.class.getDeclaredField("manifestActTheme"); theme.setAccessible(true);
            check(theme.getInt(stack.get(1)) == 0x7f120002, "destination inherited caller activity theme");
            System.out.println("PASS: navigation intent snapshot and destination manifest theme");
        } finally {
            if (previousCode == null) System.clearProperty("isakai.codeDir");
            else System.setProperty("isakai.codeDir", previousCode);
            Files.deleteIfExists(code.resolve("AndroidManifest.xml"));
            // Application attachment may create runtime data directories.
            try (java.util.stream.Stream<Path> paths = Files.walk(code)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
