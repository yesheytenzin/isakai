// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Standard text-direction heuristics. */
public final class TextDirectionHeuristics {
    public static final TextDirectionHeuristic LTR = (text, start, count) -> false;
    public static final TextDirectionHeuristic RTL = (text, start, count) -> true;
    public static final TextDirectionHeuristic FIRSTSTRONG_LTR = (text, start, count) -> false;
    public static final TextDirectionHeuristic FIRSTSTRONG_RTL = (text, start, count) -> true;
    public static final TextDirectionHeuristic ANYRTL_LTR = (text, start, count) -> {
        if (text == null) return false;
        int end = Math.min(text.length(), start + count);
        for (int i = Math.max(0, start); i < end; i++) {
            byte dir = Character.getDirectionality(text.charAt(i));
            if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
                    || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE) {
                return true;
            }
        }
        return false;
    };
    public static final TextDirectionHeuristic LOCALE = (text, start, count) -> false;

    private TextDirectionHeuristics() {}
}
