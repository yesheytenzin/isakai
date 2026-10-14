// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.translation;

/** View translation callback: show/hide/paused hooks. */
public interface ViewTranslationCallback {
    default void onShowTranslation(android.view.View view) {}

    default void onHideTranslation(android.view.View view) {}

    default void onClearTranslation(android.view.View view) {}
}
