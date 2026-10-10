// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Key-capability map: device key to label/number fallbacks. */
public class KeyCharacterMap {
    public static final int NUMERIC = 1;
    public static final int PREDICTIVE = 2;
    public static final int FULL = 3;
    public static final int SPECIAL_FUNCTION = 4;
    public static final int HEX_INPUT = 5;

    protected KeyCharacterMap() {
    }

    public static KeyCharacterMap get(int deviceId) {
        return new KeyCharacterMap();
    }

    public char getNumberAndCharacterMap(int keyCode) {
        return 0;
    }

    public static boolean deviceHasKey(int keyCode) {
        return false;
    }

    public int getKeyboardType() {
        return FULL;
    }

    public int getModifierBehavior() {
        return 0;
    }

    public char getDisplayLabel(int keyCode) {
        return 0;
    }

    public char getNumber(int keyCode) {
        return 0;
    }

    public char getMatch(int keyCode, char[] chars) {
        return 0;
    }

    public char getMatch(int keyCode, char[] chars, int metaState) {
        return 0;
    }

    public boolean getKeyData(int keyCode, KeyData results) {
        return false;
    }

    /** Output holder for getKeyData. */
    public static class KeyData {
        public char displayLabel;
        public char number;
        public char[] meta = new char[4];
    }
}
