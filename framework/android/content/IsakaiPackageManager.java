// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;

/**
 * Single-package PackageManager: answers component/package queries for the
 * bound context's own package. Activity names and themes come from the staged
 * manifest when present; uninstalled host fixtures retain default records.
 * Cross-package queries miss, matching the install snapshot boundary.
 */
public class IsakaiPackageManager extends PackageManager {
    private final Context context;

    public IsakaiPackageManager(Context context) {
        this.context = context;
    }

    private String pkg() {
        return context == null ? "" : context.getPackageName();
    }

    private boolean isOwnPackage(String packageName) {
        return packageName != null && packageName.equals(pkg());
    }

    private boolean isOwnComponent(ComponentName component) {
        return component != null && isOwnPackage(component.getPackageName());
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnComponent(component)) {
            throw new NameNotFoundException(
                    component == null ? "null" : component.flattenToString());
        }
        ActivityInfo ai = new ActivityInfo();
        ai.packageName = pkg();
        ai.name = qualifiedName(component.getClassName());
        ai.applicationInfo = getApplicationInfo(pkg(), flags);
        String codeDir = System.getProperty("isakai.codeDir", "");
        if (!codeDir.isEmpty()) {
            boolean found = false;
            try (android.content.res.XmlResourceParser parser = context.getResources().getAssets()
                    .openXmlResourceParser("AndroidManifest.xml")) {
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
                    if ("application".equals(parser.getName())) {
                        ai.applicationInfo.theme = parser.getAttributeResourceValue(ANDROID, "theme", 0);
                    } else if ("activity".equals(parser.getName())
                            && ai.name.equals(qualifiedName(parser.getAttributeValue(ANDROID, "name")))) {
                        ai.theme = parser.getAttributeResourceValue(ANDROID, "theme", 0);
                        ai.parentActivityName = qualifiedName(parser.getAttributeValue(ANDROID, "parentActivityName"));
                        found = true;
                    }
                }
            } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException failure) {
                throw new IllegalStateException("Cannot read installed activity manifest", failure);
            }
            if (!found) throw new NameNotFoundException(ai.name);
        }
        return ai;
    }

    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    private String qualifiedName(String name) {
        if (name == null) return null;
        if (name.startsWith(".")) return pkg() + name;
        return name.indexOf('.') < 0 ? pkg() + "." + name : name;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnComponent(component)) {
            throw new NameNotFoundException(
                    component == null ? "null" : component.flattenToString());
        }
        ServiceInfo si = new ServiceInfo();
        si.packageName = pkg();
        si.name = component.getClassName();
        // Real Android populates metaData only under GET_META_DATA, and a
        // component with no <meta-data> gets an empty (non-null) Bundle. This
        // runtime does not model per-component manifest metadata, so report
        // an empty one. AppCompat's AppLocalesMetadataHolderService probe
        // only sets its cached opt-in flag when metaData is non-null (or on
        // NameNotFoundException); a null here left it unset and NPE'd on
        // unbox during attachBaseContext.
        if ((flags & android.content.pm.PackageManager.GET_META_DATA) != 0) {
            si.metaData = new android.os.Bundle();
        }
        return si;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnComponent(component)) {
            throw new NameNotFoundException(
                    component == null ? "null" : component.flattenToString());
        }
        ProviderInfo pi = new ProviderInfo();
        pi.packageName = pkg();
        pi.name = component.getClassName();
        return pi;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnPackage(packageName)) throw new NameNotFoundException(packageName);
        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg();
        pi.applicationInfo = getApplicationInfo(packageName, flags);
        return pi;
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnPackage(packageName)) throw new NameNotFoundException(packageName);
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg();
        ai.dataDir = context.getDataDirString();
        ai.sourceDir = System.getProperty("isakai.codeDir");
        ai.targetSdkVersion = Integer.getInteger("isakai.targetSdk", 0);
        ai.minSdkVersion = Integer.getInteger("isakai.minSdk", 0);
        ai.flags = ApplicationInfo.FLAG_HAS_CODE;
        if (ai.sourceDir != null && !ai.sourceDir.isEmpty()) {
            try (android.content.res.XmlResourceParser parser = context.getResources().getAssets()
                    .openXmlResourceParser("AndroidManifest.xml")) {
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() == org.xmlpull.v1.XmlPullParser.START_TAG
                            && "application".equals(parser.getName())) {
                        ai.theme = parser.getAttributeResourceValue(ANDROID, "theme", 0);
                        break;
                    }
                }
            } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException failure) {
                throw new IllegalStateException("Cannot read installed application manifest", failure);
            }
        }
        return ai;
    }

    @Override
    public android.content.Context getContext() {
        return context;
    }
}
