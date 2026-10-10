// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;

/** USB endpoint shell. */
public class UsbEndpoint implements Parcelable {
    private final int address;
    public static final Parcelable.Creator<UsbEndpoint> CREATOR = new Parcelable.Creator<UsbEndpoint>() {
        @Override public UsbEndpoint createFromParcel(Parcel source) { return new UsbEndpoint(0); }
        @Override public UsbEndpoint[] newArray(int size) { return new UsbEndpoint[size]; }
    };
    public UsbEndpoint(int address) { this.address = address; }
    public int getAddress() { return address; }
    public int getDirection() { return 0; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { }
}
