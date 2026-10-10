// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/** Broadcast receiver: context саме callback contract. */
public abstract class BroadcastReceiver {
    public abstract void onReceive(Context context, Intent intent);

    public void setResultCode(int code) {}
    public int getResultCode() { return 0; }
    public void setResultData(String data) {}
    public String getResultData() { return null; }
    public void setResultExtras(android.os.Bundle extras) {}
    public android.os.Bundle getResultExtras(boolean makeMap) { return null; }
    public void abortBroadcast() {}
    public boolean getAbortBroadcast() { return false; }
}
