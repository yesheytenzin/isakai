// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Rich-content receiver contract. */
public interface OnReceiveContentListener {
    android.view.ContentInfo onReceiveContent(View view, android.view.ContentInfo payload);
}
