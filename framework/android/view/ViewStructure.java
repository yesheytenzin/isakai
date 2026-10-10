// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.os.Bundle;

/** Autofill/assist structure node: field bag only. */
public class ViewStructure {
    private int id = View.NO_ID;
    private CharSequence text;
    private CharSequence hint;
    private String className;
    private Bundle extras;
    public void setId(int id, String packageName, String typeName, String entryName) { this.id = id; }
    public int getId() { return id; }
    public void setText(CharSequence text) { this.text = text; }
    public CharSequence getText() { return text; }
    public void setHint(CharSequence hint) { this.hint = hint; }
    public CharSequence getHint() { return hint; }
    public void setClassName(String className) { this.className = className; }
    public String getClassName() { return className; }
    public Bundle getExtras() { if (extras == null) extras = new Bundle(); return extras; }
    public int addChildCount(int count) { return 0; }
    public ViewStructure newChild(int index) { return new ViewStructure(); }
    public ViewStructure asyncNewChild(int index) { return new ViewStructure(); }
}
