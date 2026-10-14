// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** State-set helpers for drawable/color-state lists (AOSP semantics). */
public final class StateSet {
    private StateSet() {}

    /** Matches any state (empty spec element). */
    public static final int[] WILD_CARD = new int[0];

    /** Matches nothing. */
    public static final int[] NOTHING = new int[]{-16842910, -16842912};

    /** True when the spec element is the wildcard (0). */
    public static boolean isWildCard(int[] stateSpec) {
        return stateSpec == null || stateSpec.length == 0 || stateSpec[0] == 0;
    }

    /**
     * Return a copy of {@code states} truncated to {@code newSize},
     * or the documented shared instances for the wildcard/empty cases.
     */
    public static int[] trimStateSet(int[] states, int newSize) {
        if (states == null) throw new IllegalArgumentException("states must not be null");
        if (newSize < 0 || newSize > states.length) {
            throw new IllegalArgumentException("newSize " + newSize + " for " + states.length);
        }
        if (newSize == 0) return WILD_CARD;
        if (newSize == states.length) return states;
        int[] trimmed = new int[newSize];
        System.arraycopy(states, 0, trimmed, 0, newSize);
        return trimmed;
    }

    /**
     * True when {@code stateSet} satisfies every element of
     * {@code stateSpec}: positive entries must be present, negative entries
     * must be absent, and 0 matches anything.
     */
    public static boolean stateSetMatches(int[] stateSpec, int[] stateSet) {
        if (stateSpec == null) return true;
        if (stateSet == null) return stateSpec.length == 0;
        for (int spec : stateSpec) {
            if (spec == 0) continue;
            boolean found = false;
            for (int state : stateSet) {
                if (state == spec) {
                    found = true;
                    break;
                }
            }
            if (spec > 0 != found) return false;
        }
        return true;
    }

    /** True when {@code stateSet} satisfies any of {@code stateSpecs}. */
    public static boolean stateSetMatches(int[][] stateSpecs, int[] stateSet) {
        if (stateSpecs == null) return true;
        for (int[] spec : stateSpecs) {
            if (stateSetMatches(spec, stateSet)) return true;
        }
        return false;
    }
}
