// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Text-direction heuristic marker. */
public interface TextDirectionHeuristic {
    boolean isRtl(CharSequence text, int start, int count);
}
