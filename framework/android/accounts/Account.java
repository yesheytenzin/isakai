// SPDX-License-Identifier: GPL-3.0-or-later
package android.accounts;

import android.os.Parcel;
import android.os.Parcelable;

/** Value type: account name plus type, parcelable. */
public class Account implements Parcelable {
    public final String name;
    public final String type;
    public static final Parcelable.Creator<Account> CREATOR = new Parcelable.Creator<Account>() {
        @Override public Account createFromParcel(Parcel source) { return new Account(source.readString(), source.readString()); }
        @Override public Account[] newArray(int size) { return new Account[size]; }
    };
    public Account(String name, String type) { this.name = name; this.type = type; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(name); dest.writeString(type); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof Account)) return false; Account a = (Account) o; return eq(name, a.name) && eq(type, a.type); }
    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
    @Override public int hashCode() { return (name == null ? 0 : name.hashCode()) ^ (type == null ? 0 : type.hashCode()); }
    @Override public String toString() { return "Account {name=" + name + ", type=" + type + "}"; }
}
