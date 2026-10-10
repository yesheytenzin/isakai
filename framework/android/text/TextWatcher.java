// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Text-change listener triple. */
public interface TextWatcher {
    void beforeTextChanged(CharSequence s, int start, int count, int after);
    void onTextChanged(CharSequence s, int start, int before, int count);
    void afterTextChanged(Editable s);
}
