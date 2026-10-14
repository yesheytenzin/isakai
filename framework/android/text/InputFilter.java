// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Text mutation filter contract. */
public interface InputFilter {
    CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend);

    class LengthFilter implements InputFilter {
        private final int max;

        public LengthFilter(int max) {
            this.max = max;
        }

        public int getMax() {
            return max;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                int dstart, int dend) {
            if (source == null) return null;
            int keep = max - (dest == null ? 0 : dest.length() - (dend - dstart));
            if (keep <= 0) return "";
            if (keep >= end - start) return null;
            if(Character.isHighSurrogate(source.charAt(start+keep-1)))keep--;
            return source.subSequence(start, start + keep);
        }
    }

    class AllCaps implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                int dstart, int dend) {
            if (source == null) return null;
            return source.subSequence(start, end).toString().toUpperCase();
        }
    }
}
