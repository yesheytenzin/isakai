// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;
/** Android's mutable spanned text contract. */
public interface Editable extends CharSequence,GetChars,Spannable,Appendable {
    Editable replace(int start,int end,CharSequence source,int sourceStart,int sourceEnd);
    Editable replace(int start,int end,CharSequence source);
    Editable insert(int where,CharSequence text,int start,int end);
    Editable insert(int where,CharSequence text);
    Editable delete(int start,int end);
    Editable append(CharSequence text);
    Editable append(CharSequence text,int start,int end);
    Editable append(char value);
    void clear();
    void clearSpans();
    void setFilters(InputFilter[] filters);
    InputFilter[] getFilters();
    default Editable replaceAll(CharSequence text) { return replace(0,length(),text==null?"":text); }
    class Factory {
        private static final Factory INSTANCE=new Factory();
        public static Factory getInstance(){return INSTANCE;}
        public Editable newEditable(CharSequence source){return new SpannableStringBuilder(source);}
    }
}
