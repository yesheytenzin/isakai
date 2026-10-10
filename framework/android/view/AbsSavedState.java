// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Saved-state chain base: wraps the super state parcelable. */
public abstract class AbsSavedState implements android.os.Parcelable {
    public static final AbsSavedState EMPTY_STATE = new AbsSavedState() {};

    private final android.os.Parcelable superState;

    private AbsSavedState() {
        this.superState = null;
    }

    protected AbsSavedState(android.os.Parcelable superState) {
        this.superState = superState == EMPTY_STATE ? null : superState;
    }

    public android.os.Parcelable getSuperState() {
        return superState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(android.os.Parcel dest, int flags) {}
}
