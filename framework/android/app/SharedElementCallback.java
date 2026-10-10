// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.view.View;
import java.util.List;
import java.util.Map;

/** Shared-element transition hooks: empty defaults. */
public abstract class SharedElementCallback {
    public void onSharedElementStart(List<View> sharedElementSnapshots, List<View> sharedElements, List<View> sharedElementNames) {}
    public void onSharedElementEnd(List<View> sharedElementSnapshots, List<View> sharedElements, List<View> sharedElementNames) {}
    public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {}
    public void onRejectSharedElements(List<View> rejectedSharedElements) {}
}
