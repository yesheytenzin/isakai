// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Result receiver: delivers results via a handler callback. */
public class ResultReceiver implements Parcelable {
    protected Handler mHandler;

    public ResultReceiver(Handler handler) {
        mHandler = handler;
    }

    protected ResultReceiver(Parcel in) {
    }

    public void send(int resultCode, Bundle resultData) {
        onReceiveResult(resultCode, resultData);
    }

    protected void onReceiveResult(int resultCode, Bundle resultData) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    public static final Parcelable.Creator<ResultReceiver> CREATOR =
            new Parcelable.Creator<ResultReceiver>() {
                @Override
                public ResultReceiver createFromParcel(Parcel source) {
                    return new ResultReceiver(source);
                }

                @Override
                public ResultReceiver[] newArray(int size) {
                    return new ResultReceiver[size];
                }
            };
}
