// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;

/** USB interface shell. */
public class UsbInterface implements Parcelable {
    private final int id;
    public static final Parcelable.Creator<UsbInterface> CREATOR = new Parcelable.Creator<UsbInterface>() {
        @Override public UsbInterface createFromParcel(Parcel source) { return new UsbInterface(0); }
        @Override public UsbInterface[] newArray(int size) { return new UsbInterface[size]; }
    };
    public UsbInterface(int id) { this.id = id; }
    public int getId() { return id; }
    public int getEndpointCount() { return 0; }
    public UsbEndpoint getEndpoint(int index) { return null; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { }
}
