// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.lang.reflect.Method;

/**
 * Pins {@link Intent#removeExtra} to the AOSP signature {@code void
 * removeExtra(String)}: translated app bytecode calls descriptor
 * {@code (Ljava/lang/String;)V}, so a chaining {@code Intent} return type
 * breaks call sites with NoSuchMethodError (Simple Notes launch failure).
 */
public final class IntentSignatureTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Method m = Intent.class.getMethod("removeExtra", String.class);
        check(m.getReturnType() == void.class,
                "removeExtra must return void, got " + m.getReturnType().getName());
        Intent intent = new Intent();
        intent.putExtra("k", "v");
        check(intent.hasExtra("k"), "extra not stored");
        intent.removeExtra("k");
        check(!intent.hasExtra("k"), "removeExtra did not remove");
        System.out.println("PASS: Intent.removeExtra has void descriptor and removes");
    }
}
