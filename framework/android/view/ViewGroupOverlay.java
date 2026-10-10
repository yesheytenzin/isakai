// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Overlay above host children: records drawables/views without attaching. */
public class ViewGroupOverlay {
    private final ViewGroup host;
    private final List<Object> entries = new ArrayList<>();
    public ViewGroupOverlay(ViewGroup host) { this.host = host; }
    public void add(Object drawable) { if (drawable != null) entries.add(drawable); }
    public void remove(Object drawable) { entries.remove(drawable); }
    public void clear() { entries.clear(); }
    public List<Object> getEntries() { return Collections.unmodifiableList(entries); }
}
