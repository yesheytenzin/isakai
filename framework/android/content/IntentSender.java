// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.os.Parcel;
import android.os.Parcelable;

/** Intent sender shell: fail-closed, never dispatches. */
public class IntentSender implements Parcelable {
    public static final Parcelable.Creator<IntentSender> CREATOR = new Parcelable.Creator<IntentSender>() {
        @Override public IntentSender createFromParcel(Parcel source) { return new IntentSender(); }
        @Override public IntentSender[] newArray(int size) { return new IntentSender[size]; }
    };
    public IntentSender() { }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { }

    public void sendIntent(Context context, int code, Intent intent,
            OnFinished onFinished, android.os.Handler handler) throws SendIntentException {
        throw new SendIntentException();
    }

    public void sendIntent(Context context, int code, Intent intent,
            OnFinished onFinished, android.os.Handler handler, String requiredPermission)
            throws SendIntentException {
        throw new SendIntentException();
    }

    public interface OnFinished {
        void onSendFinished(IntentSender intentSender, Intent intent, int resultCode,
                String resultData, android.os.Bundle resultExtras);
    }

    public static class SendIntentException extends android.util.AndroidException {
        public SendIntentException() { super(); }
        public SendIntentException(String name) { super(name); }
    }
}
