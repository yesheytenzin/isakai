// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * Pins nested key dispatch: a focused EditText inside ViewGroups must receive
 * keys dispatched at the root (ViewGroup.hasFocus recurses; dispatch follows
 * hasFocus, not isFocused). Without this, typing into app text fields silently
 * drops (OpenCalc input echo).
 */
public final class KeyDispatchTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        android.app.Application app = new android.app.Application();
        ViewGroup root = new ViewGroup(app) {};
        ViewGroup inner = new ViewGroup(app) {};
        android.widget.EditText field = new android.widget.EditText(app);
        inner.addView(field);
        root.addView(inner);
        check(field.requestFocus(), "requestFocus failed");
        check(root.hasFocus(), "root hasFocus false with focused descendant");
        check(inner.hasFocus(), "inner hasFocus false with focused child");
        check(root.findFocus()==field && inner.findFocus()==field && field.findFocus()==field,
                "findFocus must return the focused descendant");
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1, (int) '1', 0, 0, 0);
        check(root.dispatchKeyEvent(down), "root dispatch did not reach focused field");
        check("1".contentEquals(field.getText().toString()), "field text wrong: '" + field.getText() + "'");
        System.out.println("PASS: nested key dispatch reaches focused EditText");
    }
}
