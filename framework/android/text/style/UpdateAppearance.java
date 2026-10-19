// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

/**
 * Marker for spans that change how text looks when added or removed (AOSP
 * shape). Isakai draws text from the app's own paint state, so the marker
 * carries no methods; its job is to let span classes load and verify.
 */
public interface UpdateAppearance {
}
