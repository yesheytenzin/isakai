// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;
public interface GetChars extends CharSequence {
    void getChars(int start,int end,char[] dest,int destoff);
}
