// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.util.TypedValue;
import org.xmlpull.v1.XmlPullParser;

/**
 * Single-package PackageManager: answers component/package queries for the
 * bound context's own package. Activity, service and provider names come from
 * the staged manifest; undeclared components are missing.
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
        ServiceInfo si = new ServiceInfo();
        readComponent(component, flags, "service", si);
        return si;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        ProviderInfo pi = new ProviderInfo();
        readComponent(component, flags, "provider", pi);
        return pi;
    }

    private void readComponent(ComponentName component, int flags, String tag, ComponentInfo info)
            throws NameNotFoundException {
        if (!isOwnComponent(component)) {
            throw new NameNotFoundException(
                    component == null ? "null" : component.flattenToString());
        }
        info.packageName = pkg();
        info.name = qualifiedName(component.getClassName());
        String codeDir = System.getProperty("isakai.codeDir", "");
        if (codeDir.isEmpty() || java.nio.file.Files.notExists(
                java.nio.file.Path.of(codeDir, "AndroidManifest.xml"))) {
            throw new NameNotFoundException(info.name);
        }
        boolean found = false;
        try (XmlResourceParser parser = context.getResources().getAssets()
                .openXmlResourceParser("AndroidManifest.xml")) {
            int manifestDepth = -1;
            int applicationDepth = -1;
            int componentDepth = -1;
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                int depth = parser.getDepth();
                if (parser.getEventType() == XmlPullParser.END_TAG) {
                    if (depth == componentDepth) componentDepth = -1;
                    if (depth == applicationDepth) applicationDepth = -1;
                    if (depth == manifestDepth) manifestDepth = -1;
                    continue;
                }
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                if ("manifest".equals(parser.getName())) {
                    manifestDepth = depth;
                } else if (manifestDepth != -1 && depth == manifestDepth + 1
                        && "application".equals(parser.getName())) {
                    applicationDepth = depth;
                } else if (applicationDepth != -1 && depth == applicationDepth + 1
                        && tag.equals(parser.getName())
                        && info.name.equals(qualifiedName(parser.getAttributeValue(ANDROID, "name")))) {
                    found = true;
                    componentDepth = depth;
                } else if (componentDepth != -1 && depth == componentDepth + 1
                        && "meta-data".equals(parser.getName()) && (flags & GET_META_DATA) != 0) {
                    readMetaData(parser, info);
                }
            }
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException failure) {
            throw new IllegalStateException("Cannot read installed " + tag + " manifest", failure);
        }
        if (!found) throw new NameNotFoundException(info.name);
        info.applicationInfo = getApplicationInfo(pkg(), flags);
    }

    private void readMetaData(XmlResourceParser parser, ComponentInfo info) {
        String name = parser.getAttributeValue(ANDROID, "name");
        if (name == null) return;
        int resource = parser.getAttributeResourceValue(ANDROID, "resource", 0);
        if (resource != 0) {
            if (info.metaData == null) info.metaData = new android.os.Bundle();
            info.metaData.putInt(name, resource);
            return;
        }
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (!ANDROID.equals(parser.getAttributeNamespace(i))
                    || !"value".equals(parser.getAttributeName(i))) continue;
            int[] raw = parser.getAttributeRawValue(i);
            TypedValue value = new TypedValue();
            value.type = raw[0];
            value.data = raw[1];
            if (value.type == TypedValue.TYPE_REFERENCE) {
                context.getResources().getValue(value.data, value, true);
            } else if (value.type == TypedValue.TYPE_STRING) {
                value.string = parser.getAttributeValue(i);
            }
            if (value.type != TypedValue.TYPE_STRING && value.type != TypedValue.TYPE_FLOAT
                    && (value.type < TypedValue.TYPE_FIRST_INT || value.type > TypedValue.TYPE_LAST_INT)) {
                return;
            }
            if (info.metaData == null) info.metaData = new android.os.Bundle();
            if (value.type == TypedValue.TYPE_STRING) {
                info.metaData.putString(name, value.string == null ? null : value.string.toString());
            } else if (value.type == TypedValue.TYPE_FLOAT) {
                info.metaData.putFloat(name, Float.intBitsToFloat(value.data));
            } else if (value.type == TypedValue.TYPE_INT_BOOLEAN) {
                info.metaData.putBoolean(name, value.data != 0);
            } else {
                info.metaData.putInt(name, value.data);
            }
            return;
        }
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
        // The installer stages only the selected ABI slice (lib/x86_64) and
        // the launcher puts it on java.library.path, so this is the same dir
        // where System.loadLibrary resolves app libraries.
        if (ai.sourceDir != null) ai.nativeLibraryDir = ai.sourceDir + "/lib/x86_64";
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
