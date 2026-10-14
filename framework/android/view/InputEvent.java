// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;
/** Shared input event base. Source zero means the originating device is unspecified. */
public abstract class InputEvent {
    private int source;
    public abstract long getEventTime();
    public int getDeviceId(){return 0;}
    public InputDevice getDevice(){return InputDevice.getDevice(getDeviceId());}
    public int getSource(){return source;}
    public void setSource(int source){this.source=source;}
    public boolean isFromSource(int source){return (getSource() & source)==source;}
}
